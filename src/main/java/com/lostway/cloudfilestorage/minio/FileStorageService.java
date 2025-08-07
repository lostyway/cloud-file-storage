package com.lostway.cloudfilestorage.minio;

import com.lostway.cloudfilestorage.controller.dto.StorageAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageResourceDTO;
import com.lostway.cloudfilestorage.exception.dto.*;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.lostway.cloudfilestorage.utils.MinioStorageUtils.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;


    /**
     * Инициализация бакета, если он еще не создан
     */
    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
            log.info("Инициализация бакета прошла успешно");
        } catch (Exception e) {
            log.error("Ошибка инициализации Minio бакета '{}'", bucketName, e);
            throw new FileStorageException("Ошибка инициализации хранилища файлов", e);
        }
    }

    /**
     * Получение информации о запрашиваемом ресурсе. Сначала определяется: папка это или файл?
     * В зависимости от этого выбирается подход определения итогового пути и принцип поиск файла
     *
     * @param path путь к файлу/папке (может быть без / в конце, в таком случае он добавится автоматически)
     * @return Информация о ресурсе. Если это файл или папка возвращается два разных класса ответа:
     * @see StorageAnswerDTO
     * @see StorageFolderAnswerDTO
     */
    public StorageResourceDTO getInformationAboutResource(String path) {
        try {
            path = Objects.equals(path, getFullUserPath(path)) ? path : getFullUserPath(path);
            validateResourcePath(path);
            String folderPath = checkAndGetParentFolders(path);

            return isFolderPath(path)
                    ? tryReturnAsFolder(path)
                    : StorageAnswerDTO.getDefault(folderPath, getNameFromPath(path), getStatAboutFile(path).size());

        } catch (FileStorageNotFoundException |
                 InvalidFolderPathException |
                 FileStorageException |
                 ParentFolderNotFoundException e) {
            log.info("Ошибка: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.info("Ошибка: {}", e.getMessage());
            throw new FileStorageException("Ошибка инициализации хранилища файлов", e);
        }
    }

    /**
     * Создает пустую папку. Важно! Не создает попутные папки до пути.
     * Т.е. test/test2/ если папки test не будет -> будет ошибка.
     * Также не поддерживаются дубликаты
     *
     * @param folderPath путь до папки
     * @return DTO с информацией о созданной папке
     * @throws ParentFolderNotFoundException  путь до родительской папки не был найден
     * @throws ResourceInStorageAlreadyExists папка уже существует (дубликат)
     * @throws InvalidFolderPathException     невалидный путь до папки
     */

    public StorageFolderAnswerDTO createEmptyFolder(String folderPath) {
        try {
            String normalPath = getFullUserPath(folderPath);
            validatePathAndCheckIsResourceAlreadyExists(normalPath);
            String parentFolders = checkAndGetParentFolders(normalPath);
            String pathName = getNameFromPath(normalPath);

            makeEmptyFolder(normalPath);
            return StorageFolderAnswerDTO.getDefault(parentFolders, pathName);

        } catch (FileStorageException | ResourceInStorageAlreadyExists | ParentFolderNotFoundException |
                 InvalidFolderPathException | CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("Неизвестная ошибка при создании папки", e);
        }
    }

    /**
     * Загрузка файла на сервер.
     *
     * @param path путь до файла (создаст автоматически, если еще не созданы).
     * @param file файл, дубликат определяется по имени (если загружаем 12.png, а он уже есть в этой папке --> ошибка)
     * @return Информация о созданном файле
     * @throws ResourceInStorageAlreadyExists > файл уже существует в этой папке
     */
    public StorageAnswerDTO uploadFile(String path, MultipartFile file) {
        try {
            log.debug("Загрузка файла: {}, File: {}", path, file);
            String filename = getNameFromPath(getOriginalFileName(file));
            String normalizedPath = getFullUserPath(path);
            String objectName = normalizedPath + filename;
            log.debug("objectName: {}", objectName);
            log.debug("normalizedPath: {}", normalizedPath);

            validatePathAndCheckIsResourceAlreadyExists(objectName);
            makeEmptyFoldersOnPathIfNeeded(normalizedPath);
            uploadFileInFolder(file, objectName);

            return StorageAnswerDTO.getDefault(normalizedPath, filename, file.getSize());

        } catch (ResourceInStorageAlreadyExists | FileStorageNotFoundException | CantGetUserContextIdException |
                 InvalidFolderPathException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("Не удалось загрузить файл", e);
        }
    }

    /**
     * Скачивание файлов с хранилища. Можно сохранять только свои файлы (контролируется rootFolder)
     *
     * @param path путь до файла
     * @return ресурс для скачиваения
     */
    public ResponseEntity<StreamingResponseBody> downloadResource(String path, HttpServletResponse response) {
        try {
            String userPath = getFullUserPath(path);

            if (!doesResourceExists(userPath)) {
                log.error("Ресурс для скачивания не был найден: {}", userPath);
                throw new FileStorageNotFoundException("Ресурс для скачивания не был найден");
            }

            return isFolderPath(userPath)
                    ? downloadFolder(userPath, response)
                    : downloadFile(userPath, response);

        } catch (InvalidFolderPathException | FileStorageNotFoundException | CantGetUserContextIdException e) {
            response.reset();
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            throw e;
        } catch (Exception e) {
            response.reset();
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            throw new ResourceDownloadException(e.getMessage());
        }
    }

    /**
     * Удаление файла/папки (если папки, то и всех файлов + папок, которые в нее вложены)
     *
     * @param path путь до файла/папки
     */
    public void delete(String path) {
        try {
            String pathWithUser = getFullUserPath(path);

            if (!doesResourceExists(pathWithUser)) {
                throw new FileStorageNotFoundException("Папка/Файл не существует");
            }

            deleteObject(pathWithUser);

        } catch (FileStorageNotFoundException | CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("Ошибка при удалении папки", e);

        }
    }

    /**
     * Получение информации о всех файлах + папках по указанному пути.
     *
     * @param path путь до папки, по которой нужно вернуть информацию.
     * @return Коллекция DTO с файлами и папками, которые располагаются по пути.
     */
    public List<StorageResourceDTO> getFilesFromDirectory(String path) {

        String fullPath = getFullUserPath(path);

        if (!isFolderPath(fullPath)) {
            throw new InvalidFolderPathException("Необходимо ввести путь к папке, а не файлу");
        }

        if (!checkIsFolderExists(fullPath)) {
            log.warn("Папка по пути: {} не существует", fullPath);
            throw new FolderNotFoundException("Папка по указанному пути не существует");
        }

        return getAllResourcesInFolder(fullPath);
    }

    /**
     * Метод перемещения ресурса в другую папку или же переименования ресурса (если корневой путь такой же).
     *
     * @param oldPath старый путь. Пример: test/test2
     * @param newPath новый путь. Если такой же --> переименование ресурса. Примеры: test/new test/test2, test/test3 (для переименования)
     * @return Новый вид ресурса
     */
    public StorageResourceDTO replaceAction(String oldPath, String newPath) {

        String oldFullPath = getFullUserPath(oldPath);
        String newFullPath = getFullUserPath(newPath);

        preparationBeforeMoving(oldFullPath, newFullPath);

        log.debug("Перемещение ресурсов. Старое имя: {}, Новое имя: {}",
                oldFullPath, newFullPath);

        if (isFolderPath(oldFullPath)) {
            moveFolder(oldFullPath, newFullPath);
        } else {
            moveFile(oldFullPath, newFullPath);
            makeEmptyFolder(getParentFolders(oldFullPath));
        }

        return getInfoAboutResourceWithoutValidation(newFullPath);
    }

    /**
     * Получение информации о всех ресурсах в папке (не рекурсивно)
     *
     * @param fullPath путь до папки
     * @return Все файлы в папке
     */
    private List<StorageResourceDTO> getAllResourcesInFolder(String fullPath) {
        try {
            var resources = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(fullPath)
                            .delimiter("/")
                            .build()
            );

            List<StorageResourceDTO> result = new ArrayList<>();
            for (Result<Item> resource : resources) {

                String fileName = resource.get().objectName();

                if (fileName.isBlank() || fileName.equals(fullPath)) {
                    continue;
                }

                if (resource.get().size() > 0) {
                    result.add(StorageAnswerDTO.getDefault(fullPath, getNameFromPath(fileName), resource.get().size()));
                } else {
                    result.add(StorageFolderAnswerDTO.getDefault(fullPath, getNameFromPath(fileName)));
                }
            }

            return result;
        } catch (Exception e) {
            throw new FileStorageException("Ошибка при попытке получить информацию о ресурсах в папке", e);
        }
    }

    /**
     * Создание пустой папки
     *
     * @param folderPath путь до папки которую нужно создать
     */
    private void makeEmptyFolder(String folderPath) {
        log.debug("Создание пустой папки: {}", folderPath);
        if (checkIsFolderExists(folderPath)) {
            log.debug("Объект уже существует: {}", folderPath);
            return;
        }
        log.debug("Путь прошел проверку: {}", folderPath);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(folderPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                            .build()
            );
            log.debug("Пустая папка '{}' создана", folderPath);
        } catch (Exception e) {
            log.error("Не получилось создать пустую папку: {}", folderPath, e);
            throw new FileStorageException("Не получилось создать пустую папку", e);
        }

    }

    /**
     * Проверяет и возвращает путь к папке/файлу (не включая саму папку/файл)
     *
     * @param folderPath полный путь до файла
     * @return Путь до папки/файла, но без указания файла --> test/test2.txt --> rootFolder/test/
     */
    private String checkAndGetParentFolders(String folderPath) {
        String parentFolder = getParentFolders(folderPath);
        log.info("Поиск родительской папки: {} ", parentFolder);

        if (parentFolder != null && !checkIsFolderExists(parentFolder)) {
            throw new ParentFolderNotFoundException("Родительская папка не найдена: " + parentFolder);
        }

        return parentFolder;
    }

    /**
     * Проверяет, существует ли объект по указанному пути в системе (для валидации дубликатов)
     *
     * @param parentFolder путь до объекта
     * @return true --> объект существует <p>
     * false --> объект не существует
     */
    private boolean doesResourceExists(String parentFolder) {
        try {
            return isFolderPath(parentFolder)
                    ? checkIsFolderExists(parentFolder)
                    : isFileExists(parentFolder);
        } catch (FileStorageNotFoundException e) {
            log.warn("Файл или папка не была найдена: {}", parentFolder);
            return false;
        } catch (Exception e) {
            throw new FileStorageException("Ошибка проверки существования объекта", e);
        }
    }

    /**
     * Проверяет путь на наличие папок (т.к. их поиск отличается от поиска файлов)
     *
     * @param folderPath путь до предполагаемой папки
     * @return true --> папка существует<p>
     * false --> папка не существует
     */
    private boolean checkIsFolderExists(String folderPath) {
        var results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(folderPath)
                        .maxKeys(1)
                        .build()
        );
        return results.iterator().hasNext();
    }

    /**
     * Метод поиска папки и возврата DTO папки.
     *
     * @param path путь до папки
     * @return DTO для папки
     */
    private StorageFolderAnswerDTO tryReturnAsFolder(String path) {
        try {
            if (!checkIsFolderExists(path)) {
                throw new FileStorageNotFoundException("Ресурс не найден: " + path);
            }

            return StorageFolderAnswerDTO.getDefault(getParentFolders(path), getNameFromPath(path));

        } catch (FileStorageNotFoundException | InvalidFolderPathException | ParentFolderNotFoundException |
                 CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при попытке определить наличие папки '{}'", path, e);
            throw new FileStorageException("Ошибка при проверке папки", e);
        }
    }

    /**
     * Получение информации о ресурсе
     *
     * @param path путь до ресурса
     * @return Информация о ресурсе (размер и т.п.)
     */
    private StatObjectResponse getStatAboutFile(String path) {
        log.info("получение статистики о файле: {}", path);
        StatObjectResponse stat = null;
        try {
            stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            log.debug("Получена информация о файле: {}", stat);
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                log.error("Получена ошибка при поиске файла. Файл не был найден: NoSuchKey: {}", path);
                throw new FileStorageNotFoundException("Файл не был найден");
            }
        } catch (Exception e) {
            log.error("Ошибка при чтении статистики файла: {}", path);
            throw new FileStorageException("Ошибка при чтении статистики файла: " + path, e);
        }

        if (stat == null) {
            log.error("Получена ошибка при поиске файла. Файл не был найден: {}", path);
            throw new FileStorageNotFoundException("Файл не был найден");
        }

        return stat;
    }

    /**
     * Метод проверяет, существует ли файл
     *
     * @param path полный путь до файла
     * @return true -> существует<p>
     * false -> не существует
     */
    public boolean isFileExists(String path) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("Ошибка при проверке файла в MinIO", e);
        } catch (Exception e) {
            throw new RuntimeException("Неизвестная ошибка при доступе к MinIO", e);
        }
    }

    /**
     * Создание корневой папки пользователя
     */
    public void createUserRootFolder() {
        makeEmptyFolder(getRootFolder());
    }

    /**
     * Проверка пути до файла и существует ли он
     *
     * @param path путь до ресурса
     */
    private void validatePathAndCheckIsResourceAlreadyExists(String path) {
        log.debug("Проверка корректности пути validatePathAndCheckIsResourceAlreadyExists: isFile {}", path);
        validateResourcePath(path);
        log.debug("Проверка существования ресурса:, {}", path);

        if (doesResourceExists(path)) {
            log.debug("Ресурс по такому пути уже существует!:, {}", path);
            throw new ResourceInStorageAlreadyExists("Ресурс по такому пути уже существует!");
        }

        log.info("Ресурс '{}' не существует по этому пути. Можно создавать", path);
    }

    /**
     * Загрузка файла на сервер потоком
     *
     * @param file       файл
     * @param objectName путь, куда загружать
     */
    @SneakyThrows
    private void uploadFileInFolder(MultipartFile file, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
        log.info("Файл успешно загружен в '{}'", objectName);
    }

    /**
     * Создание пустых папок по пути к ресурсу
     *
     * @param path путь до ресурса
     */
    private void makeEmptyFoldersOnPathIfNeeded(String path) {
        if (path.contains("/")) {
            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();

            for (String part : parts) {
                if (part.isBlank()) {
                    continue;
                }
                currentPath.append(part).append("/");
                makeEmptyFolder(currentPath.toString());
            }
        }
    }

    /**
     * Удаление объекта
     *
     * @param pathWithUser файл или папка. На основе решения что это будет выбран метод удаления
     */
    private void deleteObject(String pathWithUser) {
        if (isFolderPath(pathWithUser)) {
            deleteFolder(pathWithUser);
        } else {
            deleteFile(pathWithUser);
        }
    }

    /**
     * Удаление папки и всего содержимого
     *
     * @param pathWithUser путь до папки
     */
    private void deleteFolder(String pathWithUser) {

        try {
            var results = getResourcesFromFolder(pathWithUser);

            for (var result : results) {
                var item = result.get();
                deleteFile(item.objectName());
                log.info("Удалена папка: {}", item.objectName());
            }
        } catch (Exception e) {
            throw new FileStorageException("Ошибка при удалении папок", e);
        }
    }

    /**
     * Удаление файла
     *
     * @param path путь до файла
     */
    public void deleteFile(String path) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            log.info("Удалён файл: {}", path);
        } catch (Exception e) {
            throw new FileStorageException("Ошибка при удалении файла", e);
        }
    }

    /**
     * Метод скачивания папок и упаковки их в ZIP архив.
     */
    private ResponseEntity<StreamingResponseBody> downloadFolder(String userPath, HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + getNameFromPath(userPath) + "\"");

        return ResponseEntity.ok()
                .body(getZipArchiveStream(userPath));
    }

    /**
     * Метод открытия потока для возврата архива с файлами в буфере
     *
     * @param userPath путь до папки
     * @return поток с данными
     */
    private StreamingResponseBody getZipArchiveStream(String userPath) {
        return out -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(out)) {

                var results = getResourcesFromFolder(userPath);

                for (Result<Item> result : results) {
                    Item item = result.get();
                    if (!item.isDir()) {
                        String objectName = item.objectName();

                        if (objectName.equals(userPath) || objectName.equals(userPath + "/")) {
                            continue;
                        }

                        String entryName = objectName.substring(userPath.length());

                        if (entryName.startsWith("/")) {
                            entryName = entryName.substring(1);
                        }

                        zipOut.putNextEntry(new ZipEntry(entryName));

                        try (InputStream in = minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(objectName)
                                        .build())) {
                            in.transferTo(zipOut);
                        } catch (Exception e) {
                            log.error("Не удалось добавить файл {} в архив: {}", objectName, e.getMessage());
                        }

                        zipOut.closeEntry();
                    }
                }
                zipOut.finish();
            } catch (Exception e) {
                log.error("Ошибка при архивации папки {}: {}", userPath, e.getMessage(), e);
            }
        };
    }

    /**
     * Метод для скачивания файлов
     *
     * @param userPath путь до файла
     * @param response ответ пользователю (куда будет отправляться поток файлов, чтобы не хранить в JVM)
     * @return поток данных с запрашиваемым ресурсом
     */
    private ResponseEntity<StreamingResponseBody> downloadFile(String userPath, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String headerValue = "attachment; filename*=UTF-8''" + URLEncoder.encode(getNameFromPath(userPath), StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setHeader("Content-Disposition", headerValue);
        StreamingResponseBody stream = out -> {
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(userPath)
                            .build())) {
                in.transferTo(out);
            } catch (Exception e) {
                log.error("Ошибка при скачивании файла: downloadFolder, {} ", userPath, e);
                throw new ResourceDownloadException("Ошибка при попытке скачать файл");
            }
        };

        return ResponseEntity.ok()
                .body(stream);
    }

    /**
     * Перемещает файл в новый путь
     *
     * @param oldFullPath старый путь
     * @param newFullPath новый путь
     */
    private void moveFile(String oldFullPath, String newFullPath) {
        try {
            CopySource source = CopySource.builder()
                    .bucket(bucketName)
                    .object(oldFullPath)
                    .build();

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(newFullPath)
                            .source(source)
                            .build()
            );

            log.info("Файл скопирован из {} в {}", oldFullPath, newFullPath);

            deleteFile(oldFullPath);

        } catch (Exception e) {
            log.error("Ошибка при перемещении файла из {} в {}: {}", oldFullPath, newFullPath, e.getMessage(), e);
            throw new FileStorageException("Ошибка при перемещении файла", e);
        }
    }

    /**
     * Перемещает папку и все содержимое в новый путь
     *
     * @param oldFolderPath старый путь
     * @param newFolderPath новый путь
     */
    private void moveFolder(String oldFolderPath, String newFolderPath) {

        if (newFolderPath.startsWith(oldFolderPath) && !newFolderPath.equals(oldFolderPath)) {
            throw new IllegalArgumentException("Нельзя переместить папку внутрь самой себя");
        }

        try {
            var results = getResourcesFromFolder(oldFolderPath);

            for (Result<Item> result : results) {
                Item item = result.get();
                String oldObjectName = item.objectName();

                String newObjectName = oldObjectName.replaceFirst(Pattern.quote(oldFolderPath), newFolderPath);

                moveFile(oldObjectName, newObjectName);

                log.debug("Объект {} перемещен в {}", oldObjectName, newObjectName);
            }
            log.info("Папка {} успешно перемещена в {}", oldFolderPath, newFolderPath);
        } catch (Exception e) {
            log.error("Ошибка при перемещении папки из {} в {}: {}", oldFolderPath, newFolderPath, e.getMessage(), e);
            throw new FileStorageException("Ошибка перемещения папки", e);
        }
    }

    /**
     * Рекурсивно достает все ресурсы из папки
     *
     * @param userPath папка, откуда нужно забрать ресурсы
     * @return Все ресурсы из папки
     */
    private Iterable<Result<Item>> getResourcesFromFolder(String userPath) {
        return minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(userPath)
                        .recursive(true)
                        .build());
    }

    /**
     * Возвращаем DTO без повторных проверок валидации
     *
     * @param resourcePath итоговый путь к ресурсу
     * @return DTO файла/папки
     */
    private StorageResourceDTO getInfoAboutResourceWithoutValidation(String resourcePath) {
        try {
            if (isFolderPath(resourcePath)) {
                return tryReturnAsFolder(resourcePath);
            } else {
                String path = checkAndGetParentFolders(resourcePath);
                String fileName = getNameFromPath(resourcePath);
                long fileSize = getStatAboutFile(resourcePath).size();
                return StorageAnswerDTO.getDefault(path, fileName, fileSize);
            }
        } catch (FileStorageNotFoundException |
                 InvalidFolderPathException |
                 FileStorageException |
                 ParentFolderNotFoundException e) {
            log.info("Ошибка: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.info("Ошибка: {}", e.getMessage());
            throw new FileStorageException("Ошибка инициализации хранилища файлов", e);
        }
    }

    /**
     * Валидация всех параметров перед перемещением в другую папку/переименованием
     *
     * @param oldFullPath старый путь откуда берем
     * @param newFullPath новый путь
     */
    private void preparationBeforeMoving(String oldFullPath, String newFullPath) {
        if (Objects.equals(oldFullPath, newFullPath)) {
            throw new SimilarResourceException("Пути и названия двух ресурсов полностью идентичны");
        }

        if (!isSameType(oldFullPath, newFullPath)) {
            throw new ResourcesNotTheSameTypeException("Ресурсы двух путей относятся к разным типам (файл/папка)");
        }

        validateResourcePath(oldFullPath);
        validateResourcePath(newFullPath);

        if (!doesResourceExists(oldFullPath)) {
            log.error("Ресурс не был найден: {}", oldFullPath);
            throw new FileStorageNotFoundException("Ресурс не существует в системе");
        }

        if (doesResourceExists(newFullPath)) {
            log.error("Ресурс уже есть в конченом пути: {}", newFullPath);
            throw new ResourceInStorageAlreadyExists("Ресурс уже существует в конченом пути");
        }
    }
}
