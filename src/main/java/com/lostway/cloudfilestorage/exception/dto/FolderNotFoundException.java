package com.lostway.cloudfilestorage.exception.dto;

public class FolderNotFoundException extends RuntimeException {
    public FolderNotFoundException(String message) {
        super(message);
    }
}
