package com.lostway.cloudfilestorage.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
/**
 * DTO который пользователь присылает при авторизации
 *
 * @param username
 * @param password
 */
public record UserLoginDTO(
        @NotBlank @Min(3) String username,
        @NotBlank @Min(3) String password
) {
}
