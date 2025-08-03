package com.lostway.cloudfilestorage.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO который пользователь присылает при регистрации
 *
 * @param username
 * @param password
 */
public record UserRegistrationDTO(
        @NotBlank @Min(3) String username,
        @NotBlank @Min(3) String password
) {
}
