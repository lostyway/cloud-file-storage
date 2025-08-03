package com.lostway.cloudfilestorage.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO который пользователь присылает при авторизации
 *
 * @param username
 * @param password
 */
public record UserLoginDTO(
        @NotBlank @Size(min = 3) String username,
        @NotBlank @Size(min = 3) String password
) {
}
