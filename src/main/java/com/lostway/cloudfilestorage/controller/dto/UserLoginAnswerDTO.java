package com.lostway.cloudfilestorage.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
/**
 * Ответ, который поступает от сервиса при успешной авторизации
 *
 * @param username пользователя
 */
public record UserLoginAnswerDTO(
        @NotBlank @Min(3) String username
) {
}
