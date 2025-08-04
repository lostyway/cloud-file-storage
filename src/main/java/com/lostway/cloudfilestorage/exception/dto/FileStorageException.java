package com.lostway.cloudfilestorage.exception.dto;

public class FileStorageException extends RuntimeException {
    public FileStorageException(String message, Exception e) {
        super(message, e);
    }
}
