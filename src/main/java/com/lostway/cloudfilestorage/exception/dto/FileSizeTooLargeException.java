package com.lostway.cloudfilestorage.exception.dto;

public class FileSizeTooLargeException extends RuntimeException {
    public FileSizeTooLargeException(String message) {
        super(message);
    }
}
