package com.lostway.cloudfilestorage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostway.cloudfilestorage.IntegrationTest;
import com.lostway.cloudfilestorage.controller.dto.UserLoginDTO;
import com.lostway.cloudfilestorage.controller.dto.UserRegistrationDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithAnonymousUser
    public void whenSignOutIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/sign-out"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenSignOutIsCorrect() throws Exception {
        mockMvc.perform(post("/api/auth/sign-out")
                        .with(user("testuser").roles("USER")))
                .andExpect(status().isNoContent());
    }

    @Test
    public void whenGetMeIsCorrect() throws Exception {
        mockMvc.perform(get("/api/auth/user/me")
                        .with(user("testuser").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @WithAnonymousUser
    public void whenGetMeIsIncorrect() throws Exception {
        mockMvc.perform(get("/api/auth/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenGetSignInIsIncorrect() throws Exception {
        UserLoginDTO userLoginDTO = new UserLoginDTO("1", "1");
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userLoginDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ошибка при вводе параметров"));
    }

    @Test
    public void whenGetSignInWithBadLoginAndPassword() throws Exception {
        UserLoginDTO userLoginDTO = new UserLoginDTO("123", "123");
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userLoginDTO)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверный логин или пароль"));
    }

    @Test
    public void whenSignUpIsCorrect() throws Exception {
        mockMvc.perform(post("/api/auth/sign-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UserRegistrationDTO("test", "123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test"));
    }

    @Test
    public void whenSignUpIsIncorrect() throws Exception {
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationDTO("1", "1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ошибка при вводе параметров"));
    }

    @Test
    public void whenSignUpIsIncorrectBecauseUserIsExist() throws Exception {
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationDTO("test", "123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test"));

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationDTO("test", "1234"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Пользователь с таким логином уже существует!"));
    }

    @Test
    public void whenGetSignInWithCorrectData() throws Exception {
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRegistrationDTO("test", "123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test"));

        UserLoginDTO userLoginDTO = new UserLoginDTO("test", "123");

        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userLoginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test"));
    }
}