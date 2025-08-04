package com.lostway.cloudfilestorage.controller.dto;

/**
 * Общий интерфейс для StorageAnswerDTO и StorageFolderAnswerDTO чтобы не дублировать логику с размерами
 */
public interface StorageResourceDTO {
    String getPath();

    String getName();

    FileType getType();
}
