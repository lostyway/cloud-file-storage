package com.lostway.cloudfilestorage.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Отличается тем, что не имеет поля size (т.к. это папка)
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class StorageFolderAnswerDTO implements StorageResourceDTO {

    private String path;
    private String name;
    private FileType type;


    public static StorageFolderAnswerDTO getDefault(String path, String name) {
        return new StorageFolderAnswerDTO(path == null ? "" : path, name, FileType.DIRECTORY);
    }
}
