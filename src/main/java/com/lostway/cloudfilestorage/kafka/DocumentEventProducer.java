package com.lostway.cloudfilestorage.kafka;

import com.lostway.jwtsecuritylib.kafka.FileUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUploadedEvent(FileUploadedEvent event) {
        kafkaTemplate.send("file-uploaded-topic", event.fileId().toString(), event);
    }
}
