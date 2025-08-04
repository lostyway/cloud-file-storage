package com.lostway.cloudfilestorage.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostway.cloudfilestorage.exception.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException
    ) throws IOException {
        log.error("Handling authentication error", authException);
        String stringResponse = getStringResponse(response);
        response.getWriter().write(stringResponse);
    }

    @SneakyThrows
    private static String getStringResponse(HttpServletResponse response) {
        try {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ErrorResponseDTO responseDTO = new ErrorResponseDTO("Пользовать не авторизован");
            return MAPPER.writeValueAsString(responseDTO);
        } catch (JsonProcessingException e) {
            log.error("Произошла ошибке при парсинге ошибки в JSON");
            throw e;
        }
    }
}
