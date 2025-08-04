package com.lostway.cloudfilestorage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostway.cloudfilestorage.IntegrationTest;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanMinioBucket() throws Exception {
        Iterable<Result<Item>> items = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()
        );

        for (Result<Item> result : items) {
            String objectName = result.get().objectName();
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        }
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsSuccessful() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsSuccessful2() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsSuccessfulWithParentFolder() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test"));

        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/test2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test2"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsSuccessfulWithParentFolder2() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test"));

        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/test2/"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test2"));
    }

    @Test
    public void whenTestWithoutAccess() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsFailedByNotHaveParentFolder() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/test2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Родительская папка не существует"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsFailedByBadPath() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test//test2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsFailedByBadPath2() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/test 2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsFailedByBadPath3() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/./test2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsFailedByBadPath4() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "test/../test2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
    }

    @Test
    @WithMockUser
    public void whenCreateEmptyDirectoryIsFailedByBadPath5() throws Exception {
        mockMvc.perform(post("/api/directory/")
                        .param("pathFolder", "/test2/"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
    }
}