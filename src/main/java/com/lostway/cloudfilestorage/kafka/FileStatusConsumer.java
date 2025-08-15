package com.lostway.cloudfilestorage.kafka;

import com.lostway.cloudfilestorage.repository.UpdateFileRepository;
import com.lostway.cloudfilestorage.repository.entity.UpdateFile;
import com.lostway.jwtsecuritylib.kafka.FileStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStatusConsumer {

    private final UpdateFileRepository updateFileRepository;

    @KafkaListener(topics = "file-status-updated-topic", groupId = "file-status-service-group")
    public void consume(FileStatusUpdatedEvent event) {
        Optional<UpdateFile> maybeFile = updateFileRepository.findById(event.fileId());
        if (maybeFile.isPresent()) {
            UpdateFile file = maybeFile.get();
            file.setStatus(event.status());
            file.setNotes(event.notes().isBlank() ? file.getNotes() : event.notes());
            file.setUpdatedAt(event.updatedAt());
            updateFileRepository.save(file);
        } else {
            log.warn("Файл не был найден: file id: {}, event: {}", event.fileId(), event.status());
            throw new RuntimeException("Файл не был найден");
        }
    }
}
