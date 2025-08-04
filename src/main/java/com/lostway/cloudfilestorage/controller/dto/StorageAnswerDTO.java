package com.lostway.cloudfilestorage.controller.dto;

public record StorageAnswerDTO(
        String path,
        String name,
        Long size,
        FileType type
) {
}
