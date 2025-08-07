package com.lostway.cloudfilestorage.controller.dto;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public enum FileType {
    DIRECTORY("DIRECTORY"), FILE("FILE");

    private final String name;

    FileType(String name) {
        this.name = name;
    }
}
