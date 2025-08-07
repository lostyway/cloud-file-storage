package com.lostway.cloudfilestorage.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StorageResource", description = "DTO для получение ресурса")
public interface StorageResourceDTO {
    @Schema(description = "Путь к ресурсу", example = "test/test2/")
    String getPath();

    @Schema(description = "Имя ресурса", example = "photo.txt")
    String getName();

    @Schema(description = "Тип ресурса", example = "FILE")
    FileType getType();
}
