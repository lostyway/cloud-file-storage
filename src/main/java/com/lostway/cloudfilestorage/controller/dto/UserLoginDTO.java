package com.lostway.cloudfilestorage.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "DTO для входа")
public record UserLoginDTO(
        @Schema(description = "Логин для входа", example = "test") @NotBlank @Size(min = 3) String username,
        @Schema(description = "Пароль для входа", example = "123") @NotBlank @Size(min = 3) String password
) {
}
