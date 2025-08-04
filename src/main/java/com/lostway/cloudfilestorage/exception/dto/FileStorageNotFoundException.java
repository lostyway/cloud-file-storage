package com.lostway.cloudfilestorage.exception.dto;

public class FileStorageNotFoundException extends RuntimeException {
    public FileStorageNotFoundException(String message) {
        super(message);
    }
}
