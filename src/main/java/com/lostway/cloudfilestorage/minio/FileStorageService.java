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
            path = getFullUserPath(path);

            if (isFolderPath(path)) {
                checkFolderPath(path);
                return tryReturnAsFolder(path);
            } else {
                validatePathToFile(path);
                checkAndGetParentFolders(path);
                StatObjectResponse stat = getStatAboutFile(path);
                return generateDTOFromTypeOfObject(path, stat);
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
            validatePathAndCheckIsResourceAlreadyExists(normalPath, false);
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

            validatePathAndCheckIsResourceAlreadyExists(objectName, true);
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

            if (!doesObjectExists(userPath)) {
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

            if (!doesObjectExists(pathWithUser)) {
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
    @SneakyThrows
    private void makeEmptyFolder(String folderPath) {
        log.debug("Создание пустой папки: {}", folderPath);
        if (checkIsFolderExists(folderPath)) {
            log.warn("Объект не существует: {}", folderPath);
            return;
        }
        log.debug("Путь прошел проверку: {}", folderPath);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(folderPath)
                        .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .build()
        );
        log.debug("Пустая папка '{}' создана", folderPath);
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
     * Проверяет существует ли объект по указанному пути в системе (для валидации дубликатов)
     *
     * @param parentFolder путь до объекта
     * @return true --> объект существует <p>
     * false --> объект не существует
     */
    private boolean doesObjectExists(String parentFolder) {
        try {
            getStatAboutFile(parentFolder);
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return checkIsFolderExists(parentFolder);
            }
            throw new FileStorageException("Ошибка проверки существования объекта", e);
        } catch (FileStorageNotFoundException e) {
            log.warn("Файл или папка не была найдена: {}", parentFolder);
            return false;
        }
        catch (Exception e) {
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
     * Генерация DTO на вывод в зависимости от того файл это или папка
     *
     * @param path путь до ресурса
     * @param stat статистика. По ней измеряется размер файла (если 0 --> папка)
     * @return DTO для ресурса
     */
    private StorageResourceDTO generateDTOFromTypeOfObject(String path, StatObjectResponse stat) {
        if (stat.size() > 0) {
            return StorageAnswerDTO.getDefault(checkAndGetParentFolders(path), getNameFromPath(path), stat.size());
        } else {
            return StorageFolderAnswerDTO.getDefault(checkAndGetParentFolders(path), getNameFromPath(path));
        }
    }

    /**
     * Метод поиска папки и возврата DTO папки.
     *
     * @param path путь до папки
     * @return DTO для папки
     */
    private StorageFolderAnswerDTO tryReturnAsFolder(String path) {
        try {
            checkAndGetParentFolders(path);

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
     * @throws ErrorResponseException ошибка, если ресурс не был найден (может папка)
     */
    @SneakyThrows
    private StatObjectResponse getStatAboutFile(String path) throws ErrorResponseException {
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
        } catch (ParentFolderNotFoundException e) {
            throw e;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                log.error("Получена ошибка при поиске файла. Файл не был найден: NoSuchKey: {}", path);
                throw new FileStorageNotFoundException("Файл не был найден");
            }
        }

        if (stat == null) {
            log.error("Получена ошибка при поиске файла. Файл не был найден: {}", path);
            throw new FileStorageNotFoundException("Файл не был найден");
        }

        return stat;
    }

    /**
     * Метод проверяет существует ли файл
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
     * Получение полного пути до ресурса пользователя
     */
    private String getFullUserPath(String path) {
        String newPath = getStandardFullRootFolder(path);
        return isFolderPath(newPath) && !newPath.endsWith("/") ? newPath + "/" : newPath;
    }

    /**
     * Проверка пути до файла и существует ли он
     *
     * @param path путь до ресурса
     */
    private void validatePathAndCheckIsResourceAlreadyExists(String path, boolean isFile) {
        log.debug("Проверка корректности пути validatePathAndCheckIsResourceAlreadyExists: isFile {}, {}", isFile, path);
        if (isFile) {
            validatePathToFile(path);
        } else {
            checkFolderPath(path);
        }
        log.debug("Проверка существования ресурса: isFile {}, {}", isFile, path);

        if (doesObjectExists(path)) {
            log.debug("Ресурс по такому пути уже существует!: isFile {}, {}", isFile, path);
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
            var results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(pathWithUser)
                            .recursive(true)
                            .build()
            );

            for (var result : results) {
                var item = result.get();
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(item.objectName())
                                .build()
                );
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

                var results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(bucketName)
                                .prefix(userPath)
                                .recursive(true)
                                .build());

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
                        }
                        zipOut.closeEntry();
                    }
                }
                zipOut.finish();
            } catch (InvalidFolderPathException | FileStorageNotFoundException | CantGetUserContextIdException e) {
                throw e;
            } catch (Exception e) {
                throw new ResourceDownloadException("Ошибка при попытке скачать папку");

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
}
