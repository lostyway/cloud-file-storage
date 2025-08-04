package com.lostway.cloudfilestorage.controller.dto;

/**
 * Отличается тем, что не имеет поля size (т.к. это папка)
 */
public record StorageFolderAnswerDTO(
        String path,
        String name,
        FileType type
) {

    public static StorageFolderAnswerDTO getDefault(String path, String name) {
        return new StorageFolderAnswerDTO(path, name, FileType.DIRECTORY);
    }
}
