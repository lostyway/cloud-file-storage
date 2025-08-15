package com.lostway.cloudfilestorage.minio;

import com.lostway.cloudfilestorage.controller.dto.StorageAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageResourceDTO;
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
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

            log.debug("Загрузка файла: {}, File: {}", file);
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
        } catch (ResourceInStorageAlreadyExists | FileStorageNotFoundException | CantGetUserContextIdException |
                 InvalidFolderPathException | BadFormatException e) {
            throw e;
        } catch (Exception e) {
            deleteFileIfExistAfterException(file, request);
            throw new FileStorageException("Не удалось загрузить файл", e);
        }
    }

    private void deleteFileIfExistAfterException(MultipartFile file, HttpServletRequest request) {
        String fileName = getNameFromPath(getOriginalFileName(file));
        String normalizedPath = getStandardFullRootFolder(null, request, jwtUtil);
        String objectName = normalizedPath + fileName;
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
                    .orElseThrow(() -> new FileStorageNotFoundException("Не удалось найти файл"));

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

    /**
     * Преобразует ресурс в его DTO
     *
     * @param item файл/папка
     * @return DTO
     */
    private StorageResourceDTO itemToDto(Item item) {
        String path = getParentFolders(item.objectName());
        String name = getNameFromPath(item.objectName());

        return isFolderPath(item.objectName())
                ? StorageFolderAnswerDTO.getDefault(path, name)
                : StorageAnswerDTO.getDefault(path, name, item.size());
    }

    /**
     * Безопасно получает файл из Result(Item)
     *
     * @param itemResult Result<Item>
     * @return Item
     */
    private Item getItemSafe(Result<Item> itemResult) {
        try {
            return itemResult.get();
        } catch (Exception e) {
            log.error("Ошибка при получении объекта из Minio", e);
            return null;
        }
    }
}
