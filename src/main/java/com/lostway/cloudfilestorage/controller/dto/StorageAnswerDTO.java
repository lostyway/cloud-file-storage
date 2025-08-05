package com.lostway.cloudfilestorage.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StorageAnswerDTO implements StorageResourceDTO {

    private String path;
    private String name;
    private long size;
    private FileType type;


    public static StorageAnswerDTO getDefault(String path, String name, long size) {
        return new StorageAnswerDTO(path == null ? "" : path, name, size, FileType.FILE);
    }
}
