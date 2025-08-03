package com.lostway.cloudfilestorage.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.experimental.Accessors;

/**
 * Ответ, который поступает от сервиса при успешной регистрации
 *
 * @param username пользователя
 */
public record UserRegistrationAnswerDTO(
        @NotBlank String username
) {
}
