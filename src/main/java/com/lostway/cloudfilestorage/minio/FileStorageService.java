package com.lostway.cloudfilestorage.minio;

import com.lostway.cloudfilestorage.controller.dto.UploadFileResponseDTO;
import com.lostway.cloudfilestorage.exception.dto.*;
import com.lostway.cloudfilestorage.mapper.KafkaMapper;
import com.lostway.cloudfilestorage.repository.OutboxKafkaRepository;
import com.lostway.cloudfilestorage.repository.UpdateFileRepository;
import com.lostway.cloudfilestorage.repository.entity.OutboxKafka;
import com.lostway.cloudfilestorage.repository.entity.UpdateFile;
import com.lostway.jwtsecuritylib.JwtUtil;
import com.lostway.jwtsecuritylib.kafka.FileUploadedEvent;
import com.lostway.jwtsecuritylib.kafka.enums.ContentType;
import com.lostway.jwtsecuritylib.kafka.enums.FileStatus;
import io.jsonwebtoken.JwtException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static com.lostway.cloudfilestorage.utils.MinioStorageUtils.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final MinioClient minioClient;
    private final JwtUtil jwtUtil;
    private final UpdateFileRepository updateFileRepository;
    private final KafkaMapper kafkaMapper;
    private final OutboxKafkaRepository outboxKafkaRepository;

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
     * Загрузка файла на сервер.
     *
     * @param file файл, дубликат определяется по имени (если загружаем 12.png, а он уже есть в этой папке --> ошибка)
     * @return Информация о созданном файле
     * @throws ResourceInStorageAlreadyExists > файл уже существует в этой папке
     */
    @Transactional
    public UploadFileResponseDTO uploadFile(MultipartFile file, HttpServletRequest request) {
        try {
            if (file.getSize() > 10 * 1024 * 1024) {
                log.warn("Размер файла слишком большой: {}MB", file.getSize() / 1024 / 1024);
                throw new FileSizeTooLargeException("Размер файла слишком большой");
            }

            String token = jwtUtil.getTokenFromHeader(request)
                    .orElseThrow(() -> new JwtException("Invalid token"));

            String email = jwtUtil.extractEmail(token);

            log.debug("Загрузка файла: {}", file);
            String fileName = getNameFromPath(getOriginalFileName(file));
            String normalizedPath = getStandardFullRootFolder(null, request, jwtUtil);
            String objectName = normalizedPath + fileName;
            log.debug("objectName: {}", objectName);
            log.debug("normalizedPath: {}", normalizedPath);

            ContentType fileType = validatePathAndCheckIsFileAlreadyExists(objectName, fileName, email);

            uploadFileInFolder(file, objectName);

            UpdateFile updateFile = UpdateFile.builder()
                    .fileId(UUID.randomUUID())
                    .fileName(fileName)
                    .fullPath(objectName)
                    .contentType(fileType)
                    .fileSize(file.getSize())
                    .uploaderEmail(email)
                    .status(FileStatus.UPLOADED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            log.info("UpdateFile: {}", updateFile);
            updateFileRepository.save(updateFile);

            FileUploadedEvent fileUpdateEvent = kafkaMapper.fromEntityToFileUpdateEvent(updateFile);
            log.info("FileUploadedEvent: {}", fileUpdateEvent);
            OutboxKafka outboxKafka = kafkaMapper.fromDtoToEntity(fileUpdateEvent);

            var outbox = outboxKafkaRepository.save(outboxKafka);
            log.info("Outbox: {}", outbox);

            return new UploadFileResponseDTO(updateFile.getFileId().toString(), "Ваш документ принят! Отчет будет направлен на почту", email);
        } catch (ResourceInStorageAlreadyExists | DocumentAlreadyExistsException | CantGetUserContextIdException |
                 InvalidFolderPathException | BadFormatException e) {
            throw e;
        } catch (Exception e) {
            String token = jwtUtil.getTokenFromHeader(request)
                    .orElseThrow(() -> new JwtException("Invalid token"));

            String email = jwtUtil.extractEmail(token);
            deleteFileIfExistAfterException(file, email, request);
            throw new FileStorageException("Не удалось загрузить файл", e);
        }
    }

    private void deleteFileIfExistAfterException(MultipartFile file, String email, HttpServletRequest request) {
        String fileName = getNameFromPath(getOriginalFileName(file));
        String normalizedPath = getStandardFullRootFolder(null, request, jwtUtil);
        String objectName = normalizedPath + fileName;

        if (updateFileRepository.findByFullPathAndUploaderEmail(objectName, email).isPresent()) {
            return;
        }

        if (isFileExists(objectName)) {
            deleteFile(objectName);
        }
    }

    private ContentType validatePathAndCheckIsFileAlreadyExists(String path, String filename, String email) {
        log.debug("Проверка корректности пути validatePathAndCheckIsFileAlreadyExists: {}", path);
        validatePathToFile(path);
        ContentType type = validateFileFormat(filename);
        log.debug("Проверка существования файла:, {}", path);

        if (isFileExists(path)) {
            log.debug("Ресурс по такому пути уже существует!:, {}", path);
            UpdateFile file = updateFileRepository.findByFullPathAndUploaderEmail(path, email)
                    .orElseThrow(() -> new DocumentAlreadyExistsException("Файл не существует в БД"));

            throw new ResourceInStorageAlreadyExists("Ресурс по такому пути уже существует. File id: %s".formatted(file.getFileId()));
        }

        log.info("Ресурс '{}' не существует по этому пути. Можно создавать", path);
        return type;
    }

    private ContentType validateFileFormat(String filename) {
        String format = filename.split("\\.")[1];
        log.debug("Формат файла: {}", format);
        return switch (format) {
            case "pdf" -> ContentType.PDF;
            case "docx" -> ContentType.DOCX;
            case "xlsx" -> ContentType.XLSX;
            default -> throw new BadFormatException("Неверный формат файла");
        };
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
    public void createUserRootFolder(HttpServletRequest request) {
        makeEmptyFolder(getRootFolder(request, jwtUtil));
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
     * Метод для скачивания файлов
     * @return поток данных с запрашиваемым ресурсом
     */
    public Resource downloadFile(UUID fileId) {
        var updateFile = updateFileRepository.findByFileId(fileId)
                .orElseThrow(() -> new RuntimeException("Файл не был найден"));

        try {
            InputStream in = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(updateFile.getFullPath())
                            .build());

            return new InputStreamResource(in);
        } catch (Exception e) {
            log.error("Ошибка при скачивании файла: downloadFolder, {} ", updateFile.getFullPath(), e);
            throw new ResourceDownloadException("Ошибка при попытке скачать файл");
        }
    }


}
