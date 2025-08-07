package com.lostway.cloudfilestorage.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "DTO для ответа пользователю")
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class StorageFolderAnswerDTO implements StorageResourceDTO {

    @Schema(description = "Путь к папке", example = "/test/test1/")
    private String path;

    @Schema(description = "Имя папки", example = "test")
    private String name;

    @Schema(description = "Тип файла", example = "DIRECTORY")
    private FileType type;


    public static StorageFolderAnswerDTO getDefault(String path, String name) {
        return new StorageFolderAnswerDTO(path == null || path.equals("/") ? "" : path, name, FileType.DIRECTORY);
    }
}
