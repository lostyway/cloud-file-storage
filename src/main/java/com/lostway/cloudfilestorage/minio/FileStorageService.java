package com.lostway.cloudfilestorage.minio;

import com.lostway.cloudfilestorage.controller.dto.StorageAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageResourceDTO;
import com.lostway.cloudfilestorage.exception.dto.*;
import com.lostway.cloudfilestorage.security.CustomUserDetails;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.List;

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

            if (isFolderPath(path)) {
                path = getNormilizePath(path);
                checkFolderPath(path);
                return tryReturnAsFolder(path);
            } else {
                path = getNormilizePath(path, true);
                validatePathToFile(path);
                StatObjectResponse stat = getStatObjectResponse(path);
                return generateDTOFromTypeOfObject(path, stat);
            }

        } catch (FileStorageNotFoundException |
                 InvalidFolderPathException |
                 FileStorageException |
                 ParentFolderNotFoundException e) {
            throw e;
        } catch (Exception e) {
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
     * @throws ParentFolderNotFoundException путь до родительской папки не был найден
     * @throws FolderAlreadyExistsException  папка уже существует (дубликат)
     * @throws InvalidFolderPathException    невалидный путь до папки
     */

    public StorageFolderAnswerDTO createEmptyFolder(String folderPath) {
        try {
            String normalPath = getNormilizePath(folderPath);
            validatePathAndCheckIsResourceAlreadyExists(normalPath, false);
            String parentFolders = checkAndGetParentFolders(normalPath);
            String pathName = getFileName(normalPath);

            makeEmptyFolder(normalPath);
            return StorageFolderAnswerDTO.getDefault(parentFolders, pathName);

        } catch (FileStorageException | FolderAlreadyExistsException | ParentFolderNotFoundException |
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
            String filename = Paths.get(getOriginalFileName(file)).getFileName().toString();
            String normalizedPath = getNormilizePath(path);
            String objectName = normalizedPath + filename;

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
     * Удаление файла/папки (если папки, то и всех файлов + папок, которые в нее вложены)
     *
     * @param path путь до файла/папки
     */
    public void delete(String path) {
        try {
            String pathWithUser = getNormilizePath(path);

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
        return null;
    }

    /**
     * Создание пустой папки
     *
     * @param folderPath путь до папки которую нужно создать
     */
    @SneakyThrows
    private void makeEmptyFolder(String folderPath) {
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
     * Проверка пути до папки
     *
     * @param folderPath путь к папке (используется только для папок, для файлов не подойдет)
     */
    private static void checkFolderPath(String folderPath) {
        if (!folderPath.matches("^(?!.*//)(?!.*\\.{1,2})([\\w\\-]+/)*$")) {
            throw new InvalidFolderPathException("Недопустимый путь к папке: " + folderPath);
        }
    }

    /**
     * Проверка пути до файла
     *
     * @param path путь к файлу (используется только для файлов, для папок не подойдет)
     */
    private static void validatePathToFile(String path) {
        if (!path.matches("^(?!.*//)(?!.*(?:^|/)\\.)(?!.*(?:^|/)\\.\\.)([a-zA-Z0-9_-]+/)*[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+$")) {
            throw new InvalidFolderPathException("Недопустимый путь: " + path);
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
        log.debug("Поиск родительской папки: {} ", parentFolder);

        if (parentFolder != null && !doesObjectExists(parentFolder)) {
            throw new ParentFolderNotFoundException("Родительская папка не найдена: " + parentFolder);
        }

        return parentFolder;
    }


    /**
     * Возвращает путь до файла не включая этот файл<p>
     * test/test2.txt --> rootFolder/test/
     *
     * @param folderPath путь до папки (включая файл)
     * @return путь до папки, не включая сам файл/папку<p>
     * test/test2.txt --> rootFolder/test/<p>
     * test/test2/ --> rootFolder/test/
     */
    private String getParentFolders(String folderPath) {
        int lastFlash = folderPath.lastIndexOf("/", folderPath.length() - 2);
        if (lastFlash > 0) {
            return folderPath.substring(0, lastFlash + 1);
        }
        return null;
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
            getStatObjectResponse(parentFolder);
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return checkAllMaybeAFolder(parentFolder);
            }
            throw new FileStorageException("Ошибка проверки существования объекта", e);
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
    private boolean checkAllMaybeAFolder(String folderPath) {
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
     * Получение имени папки/файла, исключая его путь.
     *
     * @param folderPath полный путь до папки/файла
     * @return Имя файла/папки test/test2.txt --> test2.txt
     */
    private static String getFileName(String folderPath) {
        String trimmedPath = folderPath.endsWith("/")
                ? folderPath.substring(0, folderPath.length() - 1)
                : folderPath;

        String[] parts = trimmedPath.split("/");
        return parts[parts.length - 1];
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
            return StorageAnswerDTO.getDefault(checkAndGetParentFolders(path), getFileName(path), stat.size());
        } else {
            return StorageFolderAnswerDTO.getDefault(checkAndGetParentFolders(path), getFileName(path));
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

            if (!checkAllMaybeAFolder(path)) {
                throw new FileStorageNotFoundException("Ресурс не найден: " + path);
            }

            return StorageFolderAnswerDTO.getDefault(getParentFolders(path), getFileName(path));

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
    private StatObjectResponse getStatObjectResponse(String path) throws ErrorResponseException {
        var stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build()
        );
        log.debug("Получена информация о файле: {}", stat);
        return stat;
    }

    /**
     * Создание корневой папки пользователя
     */
    public void createUserRootFolder() {
        makeEmptyFolder(getRootFolder());
    }

    /**
     * Получение корневой папки по userId из контекста.
     *
     * @return ID текущего пользователя
     */
    public String getRootFolder() {
        Long userId = getCurrentUserId();
        return "user-" + userId + "-files/";
    }

    /**
     * Получение полного пути.
     *
     * @param path путь без корневой папки.
     * @return Полный путь с корневой папкой. Т.е. если было: /test/test2/ --> rootFolder/test/test2/
     */
    private String getFullPathForUser(String path) {
        String sanitizedRelativePath = path.replaceAll("^/+", "");
        return getRootFolder() + sanitizedRelativePath;
    }

    /**
     * Получение userID текущего пользователя.
     *
     * @return UserID из контекста безопасности
     */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new CantGetUserContextIdException("Аутентифицированный пользователь не найден или имеет неверный тип");
    }

    /**
     * Проверка, является ли путь путем до папки или файла
     *
     * @param path путь до ресурса
     * @return true --> это папка<p>
     * false --> это файл
     */
    private boolean isFolderPath(String path) {
        if (path.endsWith("/")) {
            return true;
        }

        int lastSlashIndex = path.lastIndexOf('/');
        String lastSegment = lastSlashIndex == -1 ? path : path.substring(lastSlashIndex + 1);

        return !lastSegment.contains(".");
    }


    /**
     * Получение нормализованного пути (путь уже с root папкой и правильными /)
     *
     * @param path   путь
     * @param isFile это путь до файла? Если да, то / в конце проставляться не будет.
     * @return Полный путь до ресурса
     */
    private String getNormilizePath(String path, boolean isFile) {
        if (path == null || path.isBlank()) {
            path = "";
        }
        path = getFullPathForUser(path);

        if (!isFile) {
            path = path.endsWith("/") ? path : path + "/";
        }

        return path;
    }

    /**
     * Перегруженный метод. Получение полного пути до папки. Сделано для обратной совместимости.
     *
     * @param path путь
     * @return Полный путь до ресурса
     */
    private String getNormilizePath(String path) {
        if (path == null || path.isBlank()) {
            path = "";
        }
        path = getFullPathForUser(path);

        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * Получение полного имени ресурса
     */
    private String getOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла отсутствует");
        }
        return originalFileName;
    }

    /**
     * Проверка пути до файла и существует ли он
     *
     * @param path путь до ресурса
     */
    private void validatePathAndCheckIsResourceAlreadyExists(String path, boolean isFile) {
        if (isFile) {
            validatePathToFile(path);
        } else {
            checkFolderPath(path);
        }

        if (doesObjectExists(path)) {
            throw new ResourceInStorageAlreadyExists("Ресурс по такому пути уже существует!");
        }

        log.info("Ресурс '{}' не существует по этому пути. Можно создавать", path);
    }

    @SneakyThrows
    private void uploadFileInFolder(MultipartFile file, String objectName) {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        log.info("Файл успешно загружен в '{}'", objectName);
    }

    /**
     * Создание пустых папок по пути к ресурсу
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
}
