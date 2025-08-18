package com.lostway.cloudfilestorage.scheduler;

import com.lostway.cloudfilestorage.kafka.DocumentEventProducer;
import com.lostway.cloudfilestorage.mapper.KafkaMapper;
import com.lostway.cloudfilestorage.minio.FileStorageService;
import com.lostway.cloudfilestorage.repository.OutboxKafkaRepository;
import com.lostway.cloudfilestorage.repository.UpdateFileRepository;
import com.lostway.cloudfilestorage.repository.entity.OutboxKafka;
import com.lostway.cloudfilestorage.repository.entity.UpdateFile;
import com.lostway.jwtsecuritylib.kafka.enums.FileStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxSenderScheduler {
    private final DocumentEventProducer documentEventProducer;
    private final OutboxKafkaRepository outboxKafkaRepository;
    private final KafkaMapper mapper;
    private final FileStorageService fileStorageService;
    private final UpdateFileRepository updateFileRepository;

    @Value("${scheduler-batch-size}")
    private int BATCH_SIZE;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void sendDocumentEvents() {
        var outboxEvents = outboxKafkaRepository.getActualKafkaEventsToSend(Pageable.ofSize(BATCH_SIZE));
        var events = mapper.fromEntitiesToEvents(outboxEvents);

        events.stream()
                .filter(Objects::nonNull)
                .forEach(documentEventProducer::sendUploadedEvent);
        log.info("Events отправлены: {}", events.toArray());

        var ids = outboxEvents.stream()
                .filter(Objects::nonNull)
                .map(OutboxKafka::getId)
                .toList();

        int marked = outboxKafkaRepository.markEventsAsProcessed(ids);
        log.info("Ивенты помечены как выполненные: {}", marked);
    }

    /**
     * Очистка бд от неактуальных данных, очищать лучше раз в неделю, для тестов раз в минуту
     */
    @Scheduled(cron = "${cleaner-outbox-base-schedule-cron}")
    @Transactional
    public void clearOutboxBase() {
        //todo позже заменить на неделю
//        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        Instant weekAgo = Instant.now().minus(Duration.ofMinutes(1));
        var outboxEvents = outboxKafkaRepository.getOldOutboxEvents(weekAgo, Pageable.ofSize(BATCH_SIZE));
        log.info("Отправленные events будут удалены по сроку годности: {}", outboxEvents.toArray());
        outboxKafkaRepository.deleteAll(outboxEvents);
        log.info("Events удалены");
    }


    /**
     * Очистка s3 от неактуальных данных, очищать лучше раз в неделю, для тестов раз в минуту
     */
    @Scheduled(cron = "${cleaner-outbox-base-schedule-cron}")
    @Transactional
    @Async
    public void clearS3() {
        //todo позже заменить на неделю
//        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        Instant weekAgo = Instant.now().minus(Duration.ofMinutes(1));
        var list = updateFileRepository.findAllByStatusInAndCreatedAtAfter(List.of(FileStatus.COMPLETED, FileStatus.FAILED), weekAgo);

        log.info("Будут удалены даные из Бд и minio: {}", Arrays.toString(list.toArray()));
        if (list.isEmpty()) {
            return;
        }

        list.stream()
                .filter(Objects::nonNull)
                .map(UpdateFile::getFullPath)
                .filter(Objects::nonNull)
                .forEach(fileStorageService::deleteFile);

        updateFileRepository.deleteAll(list);
    }
}
