package com.lostway.cloudfilestorage.exception.dto;

public class FileInStorageAlreadyExists extends RuntimeException {
    public FileInStorageAlreadyExists(String message) {
        super(message);
    }
}
