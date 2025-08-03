package com.lostway.cloudfilestorage.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ответ, который поступает от сервиса при успешной авторизации
 *
 * @param username пользователя
 */
public record UserLoginAnswerDTO(
        @NotBlank @Size(min = 3) String username
) {
}
