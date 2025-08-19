package com.lostway.cloudfilestorage.controller.dto;

import com.lostway.jwtsecuritylib.kafka.enums.FileStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ActualStatusResponseDTO(
        UUID fileId,
        FileStatus status,
        String fileName,
        Instant createdAt,
        Instant updatedAt,
        String notes
) implements Serializable {
}
