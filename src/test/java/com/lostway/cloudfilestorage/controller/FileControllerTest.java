package com.lostway.cloudfilestorage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostway.cloudfilestorage.IntegrationTest;
import com.lostway.cloudfilestorage.controller.dto.FileType;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Nested
    class createEmptyFolderMethod {
        String apiPath = "/api/directory/";

        @Test
        public void whenCreateEmptyDirectoryIsSuccessful() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsSuccessful2() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsSuccessfulWithParentFolder() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsSuccessfulWithParentFolder2() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/test2/"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));
        }

        @Test
        @WithAnonymousUser
        public void whenTestWithoutAccess() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByNotHaveParentFolder() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/test2"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test//test2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath2() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/test 2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath3() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/./test2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath4() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "test/../test2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath5() throws Exception {
            mockMvc.perform(post(apiPath)
                            .param("pathFolder", "/test2/"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }
    }

    @Nested
    class getInformationAboutResourceMethod {
        String getInformation = "/api/resource/";
        String makeEmptyFolder = "/api/directory/";

        @Test
        @WithAnonymousUser
        public void whenTestWithoutAccess() throws Exception {
            mockMvc.perform(post(getInformation)
                            .param("path", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        public void whenGetInformationAboutFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(getInformation)
                            .param("path", "test/test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value("test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.toString()));
        }

        @Test
        public void whenGetInformationAboutFolder2() throws Exception {
            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(get(getInformation)
                            .param("path", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(""))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.toString()));
        }

        @Test
        public void whenGetInformationAboutFolderWithIncorrectFolderName() throws Exception {
            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(getInformation)
                            .param("path", "test/test3"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        public void whenGetInformationAboutFolderWithIncorrectParentFolder() throws Exception {
            mockMvc.perform(get(getInformation)
                            .param("path", "test/test3"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));
        }

        @Test
        public void whenGetInformationAboutFolderWithIncorrectFileInRoot() throws Exception {
            mockMvc.perform(get(getInformation)
                            .param("path", "test"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        public void whenTryToFindWithIncorrectFileFolderName() throws Exception {
            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(getInformation)
                            .param("path", "test/test2/test3"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        public void whenTryToFindSuccess() throws Exception {
            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(getInformation)
                            .param("path", "test/test2/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value("test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.toString()));

            mockMvc.perform(get(getInformation)
                            .param("path", "test/test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value("test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.toString()));
        }

        @Test
        public void whenTryToFindInRootFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolder)
                            .param("pathFolder", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(get(getInformation)
                            .param("path", "test/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(""))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.toString()));
        }
    }

}