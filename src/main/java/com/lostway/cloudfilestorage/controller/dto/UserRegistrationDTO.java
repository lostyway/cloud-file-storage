package com.lostway.cloudfilestorage.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "DTO для регистрации")
public record UserRegistrationDTO(
        @Schema(description = "Логин для регистрации (должен быть уникальным)", example = "test") @NotBlank @Size(min = 3) String username,
        @Schema(description = "Пароль для регистрации", example = "123") @NotBlank @Size(min = 3) @NotBlank @Size(min = 3) String password
) {
}
