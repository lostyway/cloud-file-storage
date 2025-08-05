package com.lostway.cloudfilestorage.exception.dto;

public class ResourceInStorageAlreadyExists extends RuntimeException {
    public ResourceInStorageAlreadyExists(String message) {
        super(message);
    }
}
