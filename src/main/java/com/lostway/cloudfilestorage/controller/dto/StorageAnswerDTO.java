package com.lostway.cloudfilestorage.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "DTO для ответа пользователю")
@Data
@AllArgsConstructor
public class StorageAnswerDTO implements StorageResourceDTO {
    @Schema(description = "Путь к ресурсу", example = "/test/test1/")
    private String path;

    @Schema(description = "Имя файла", example = "test.txt")
    private String name;

    @Schema(description = "Размер файла", example = "1220")
    private long size;

    @Schema(description = "Тип файла", example = "FILE")
    private FileType type;


    public static StorageAnswerDTO getDefault(String path, String name, long size) {
        return new StorageAnswerDTO(path == null || path.equals("/") ? "" : path, name, size, FileType.FILE);
    }
}
