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
import java.io.InputStream;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

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

    public StorageResourceDTO getInformationAboutResource(String path) {
        try {
            validateAllPath(path);
            StatObjectResponse stat = getStatObjectResponse(path);
            return generateDTOFromTypeOfObject(path, stat);
        } catch (ErrorResponseException e) {
            if (!e.errorResponse().code().equals("NoSuchKey")) {
                throw new FileStorageException("Ошибка получения объекта", e);
            }

            return tryReturnAsFolder(path);

        } catch (FileStorageNotFoundException |
                 InvalidFolderPathException |
                 FileStorageException |
                 ParentFolderNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при попытке получить информацию о файле '{}'", path, e);
            throw new FileStorageException("Ошибка инициализации хранилища файлов", e);
        }
    }

    public StorageFolderAnswerDTO createEmptyFolder(String folderPath) {
        try {
            String normalPath = getNormilizePath(folderPath);
            checkFolderPath(normalPath);
            String parentFolders = checkAndGetParentFolders(normalPath);
            String pathName = getPathName(normalPath);

            try {
                getStatObjectResponse(normalPath);
                throw new FolderAlreadyExistsException("Папка уже существует: " + normalPath);
            } catch (ErrorResponseException e) {
                if (!e.errorResponse().code().equals("NoSuchKey")) {
                    throw new FileStorageException("Ошибка проверки существования папки", e);
                }
            }

            makeEmptyFolder(normalPath);
            return StorageFolderAnswerDTO.getDefault(parentFolders, pathName);

        } catch (FileStorageException | FolderAlreadyExistsException | ParentFolderNotFoundException |
                 InvalidFolderPathException | CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            log.error("Неизвестная ошибка при создании папки '{}'", folderPath, e);
            throw new FileStorageException("Неизвестная ошибка при создании папки", e);
        }
    }

    public StorageAnswerDTO uploadFile(String path, MultipartFile file) {
        try {
            String filename = Paths.get(getOriginalFileName(file)).getFileName().toString();
            String normalizedPath = getNormilizePath(path);
            String objectName = normalizedPath + filename;

            validatePathAndCheckIsFileAlreadyExists(objectName);
            makeEmptyFoldersOnPathIfNeeded(normalizedPath);
            uploadFileInFolder(file, objectName);

            return StorageAnswerDTO.getDefault(normalizedPath, filename, file.getSize());

        } catch (FileInStorageAlreadyExists | FileStorageNotFoundException | CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при загрузке ресурса", e);
            throw new FileStorageException("Не удалось загрузить файл", e);
        }
    }

    private String getNormilizePath(String path) {
        if (path == null || path.isBlank()) {
            path = "";
        }
        path = getFullPathForUser(path);
        return path.endsWith("/") ? path : path + "/";
    }


    private String getOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла отсутствует");
        }
        return originalFileName;
    }

    private void validatePathAndCheckIsFileAlreadyExists(String objectName) {
        validateAllPath(objectName);

        if (doesObjectExists(objectName)) {
            throw new FileInStorageAlreadyExists("Файл по такому пути уже существует!");
        }

        log.info("Файл '{}' не существует по этому пути. Можно создавать", objectName);
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

    public void delete(String path) {
        String pathWithUser = getNormilizePath(path);
        if (!doesObjectExists(pathWithUser)) {
            throw new FileStorageNotFoundException("Папка/Файл не существует");
        }

        if (!pathWithUser.endsWith("/")) {
            deleteFile(pathWithUser);
        }

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
                log.info("Удалён файл: {}", item.objectName());
            }

        } catch (FileStorageNotFoundException | CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при удалении директории '{}'", pathWithUser, e);
            throw new FileStorageException("Ошибка при удалении папки", e);
        }
    }

    public InputStream downloadFile(String filename) {
        try {
            log.info("Скачивание файла: {}", filename);
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .build()
            );
        } catch (Exception e) {
            log.error("Произошла ошибка при попытке скачать файл '{}'", filename, e);
            throw new FileStorageException("Ошибка при скачивании файла", e);
        }
    }


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
            log.error("Ошибка при удалении файла '{}'", path, e);
            throw new FileStorageException("Ошибка при удалении файла", e);
        }
    }


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
     * Проверяет, что: путь, начинается с буквы, цифры или _ (не с /),
     * <p>
     * состоит из сегментов, разделённых одиночными слэшами (/),
     * <p>
     * каждый сегмент — из букв, цифр, _ или -.
     * <p>
     * Путь не содержит:, // (двойные слэши), .. (ссылок на родительский каталог), . (ссылок на текущий каталог)
     * <p>
     * Путь заканчивается слэшем / (если это "папка")
     */
    private static void checkFolderPath(String folderPath) {
        if (!folderPath.matches("^(?!.*//)(?!.*\\.{1,2})([\\w\\-]+/)*$")) {
            throw new InvalidFolderPathException("Недопустимый путь к папке: " + folderPath);
        }
    }

    private static void validateAllPath(String path) {
        if (!path.matches("^(?!.*//)(?!.*\\.{2,}/)(?!.*/\\.{1,2}$)([a-zA-Z0-9_\\-./()]+(/[a-zA-Z0-9_\\-./()]+)*)$")) {
            throw new InvalidFolderPathException("Недопустимый путь: " + path);
        }
    }

    private String checkAndGetParentFolders(String folderPath) {
        String parentFolder = getParentFolder(folderPath);

        if (parentFolder != null && !doesObjectExists(parentFolder)) {
            throw new ParentFolderNotFoundException("Родительская папка не найдена: " + parentFolder);
        }

        return parentFolder;
    }

    private String getParentFolder(String folderPath) {
        int lastFlash = folderPath.lastIndexOf("/", folderPath.length() - 2);
        if (lastFlash > 0) {
            return folderPath.substring(0, lastFlash + 1);
        }
        return null;
    }

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

    private static String getPathName(String folderPath) {
        String trimmedPath = folderPath.endsWith("/")
                ? folderPath.substring(0, folderPath.length() - 1)
                : folderPath;

        String[] parts = trimmedPath.split("/");
        return parts[parts.length - 1];
    }

    private StorageResourceDTO generateDTOFromTypeOfObject(String path, StatObjectResponse stat) {
        if (stat.size() > 0) {
            return StorageAnswerDTO.getDefault(checkAndGetParentFolders(path), getPathName(path), stat.size());
        } else {
            return StorageFolderAnswerDTO.getDefault(checkAndGetParentFolders(path), getPathName(path));
        }
    }

    private StorageFolderAnswerDTO tryReturnAsFolder(String path) {
        try {
            String folderPath = getNormilizePath(path);

            checkAndGetParentFolders(folderPath);

            if (!checkAllMaybeAFolder(folderPath)) {
                throw new FileStorageNotFoundException("Ресурс не найден: " + path);
            }

            return StorageFolderAnswerDTO.getDefault(getParentFolder(folderPath), getPathName(path));

        } catch (FileStorageNotFoundException | InvalidFolderPathException | ParentFolderNotFoundException |
                 CantGetUserContextIdException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при попытке определить наличие папки '{}'", path, e);
            throw new FileStorageException("Ошибка при проверке папки", e);
        }
    }

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

    public void createUserRootFolder() {
        makeEmptyFolder(getRootFolder());
    }

    public String getRootFolder() {
        Long userId = getCurrentUserId();
        return "user-" + userId + "-files/";
    }

    private String getFullPathForUser(String path) {
        String sanitizedRelativePath = path.replaceAll("^/+", "");
        return getRootFolder() + sanitizedRelativePath;
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new CantGetUserContextIdException("Аутентифицированный пользователь не найден или имеет неверный тип");
    }
}
