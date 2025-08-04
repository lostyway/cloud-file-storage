package com.lostway.cloudfilestorage.minio;

import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.exception.dto.FileStorageException;
import com.lostway.cloudfilestorage.exception.dto.FolderAlreadyExistsException;
import com.lostway.cloudfilestorage.exception.dto.InvalidFolderPathException;
import com.lostway.cloudfilestorage.exception.dto.ParentFolderNotFoundException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final MinioClient minioClient;

    private static final String BUCKET_NAME = "files";

    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
            }
            log.info("Инициализация бакета прошла успешно");
        } catch (Exception e) {
            log.error("Ошибка инициализации Minio бакета '{}'", BUCKET_NAME, e);
            throw new FileStorageException("Ошибка инициализации хранилища файлов", e);
        }
    }

    public void uploadFile(String filename, InputStream inputStream, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(filename)
                            .stream(inputStream, -1, 10485760)
                            .contentType(contentType)
                            .build()
            );
            log.info("Загрузка файла: {}", filename);
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла '{}'", filename, e);
            throw new FileStorageException("Не удалось загрузить файл", e);
        }
    }

    public InputStream downloadFile(String filename) {
        try {
            log.info("Скачивание файла: {}", filename);
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(filename)
                            .build()
            );
        } catch (Exception e) {
            log.error("Произошла ошибка при попытке скачать файл '{}'", filename, e);
            throw new FileStorageException("Ошибка при скачивании файла", e);
        }
    }

    public void deleteFile(String filename) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(filename)
                            .build()
            );
            log.info("Удаление файла: {}", filename);
        } catch (Exception e) {
            log.error("Произошла ошибка при удалении файла'{}'", filename, e);
            throw new FileStorageException("Произошла ошибка при удалении файла", e);
        }
    }


    public StorageFolderAnswerDTO createEmptyFolder(String folderPath) {
        try {
            if (folderPath == null || folderPath.isBlank()) {
                throw new InvalidFolderPathException("Путь к папке не может быть пустым");
            }

            if (!folderPath.endsWith("/")) {
                folderPath = folderPath.concat("/");
            }

            checkPath(folderPath);

            String parentFolders = checkAndGetParentFolders(folderPath);

            String[] pathNameArr = folderPath.split("/");
            String pathName = pathNameArr[pathNameArr.length - 1];

            try {
                minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(BUCKET_NAME)
                                .object(folderPath)
                                .build()
                );
                throw new FolderAlreadyExistsException("Папка уже существует: " + folderPath);
            } catch (ErrorResponseException e) {
                if (!e.errorResponse().code().equals("NoSuchKey")) {
                    throw new FileStorageException("Ошибка проверки существования папки", e);
                }
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(folderPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .contentType("application/x-directory")
                            .build()
            );
            log.info("Пустая папка '{}' создана", folderPath);
            return StorageFolderAnswerDTO.getDefault(parentFolders, pathName);

        } catch (FileStorageException | FolderAlreadyExistsException | ParentFolderNotFoundException |
                 InvalidFolderPathException e) {
            throw e;
        } catch (Exception e) {
            log.error("Неизвестная ошибка при создании папки '{}'", folderPath, e);
            throw new FileStorageException("Неизвестная ошибка при создании папки", e);
        }
    }

    /**
     * Проверяет, что: путь, начинается с буквы, цифры или _ (не с /),
     *              <p>
     *              состоит из сегментов, разделённых одиночными слэшами (/),
     *              <p>
     *              каждый сегмент — из букв, цифр, _ или -.
     *              <p>
     *              Путь не содержит:, // (двойные слэши), .. (ссылок на родительский каталог), . (ссылок на текущий каталог)
     *              <p>
     *              Путь заканчивается слэшем / (если это "папка")
     */
    private static void checkPath(String folderPath) {
        if (!folderPath.matches("^(?!.*//)(?!.*\\.{1,2})([\\w\\-]+/)*$")) {
            throw new InvalidFolderPathException("Недопустимый путь к папке: " + folderPath);
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
            minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(parentFolder)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            return e.errorResponse().code().equals("NoSuchKey")
                    ? false
                    : throwAsFileStorageException(e);
        } catch (Exception e) {
            throw new FileStorageException("Ошибка проверки родительской папки", e);
        }
    }

    private boolean throwAsFileStorageException(ErrorResponseException e) {
        throw new FileStorageException("Ошибка проверки существования объекта", e);
    }
}
