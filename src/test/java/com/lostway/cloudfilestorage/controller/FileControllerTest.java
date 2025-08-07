package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.IntegrationTest;
import com.lostway.cloudfilestorage.controller.dto.FileType;
import com.lostway.cloudfilestorage.security.CustomUserDetails;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static com.lostway.cloudfilestorage.utils.MinioStorageUtils.getNameFromPath;
import static com.lostway.cloudfilestorage.utils.MinioStorageUtils.getRootFolder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
class FileControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

    private final String makeEmptyFolderOrGetInformationAboutFolder = "/api/directory";
    private final String deleteApi = "/api/resource";
    private final String uploadOrGetInformation = "/api/resource";
    private final String downloadApi = "/api/resource/download";
    private final String replaceApi = "/api/resource/move";
    private final String searchApi = "/api/resource/search";

    private final MockMultipartFile file = new MockMultipartFile(
            "file",
            "C:/test.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "Hello, World".getBytes()
    );

    private String rootFolder;

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

        CustomUserDetails customUserDetails = new CustomUserDetails(
                1L, "username", "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                customUserDetails, null, customUserDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(auth);

        this.rootFolder = getRootFolder();
    }

    @Nested
    @DisplayName("Проверка метода создания пустой папки")
    class CreateEmptyFolderMethod {

        @Test
        public void whenCreateEmptyDirectoryIsSuccessful() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsSuccessful2() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsSuccessfulWithParentFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsSuccessfulWithParentFolder2() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2/"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));
        }


        @Test
        public void whenCreateEmptyDirectoryIsFailedByNotHaveParentFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test//test2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath2() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/./test2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        public void whenCreateEmptyDirectoryIsFailedByBadPath3() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/../test2"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }
    }

    @Nested
    @DisplayName("Проверка метода получения информации о ресурсе")
    class GetInformationAboutResourceMethod {

        @Test
        public void whenGetInformationAboutFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));
        }

        @Test
        public void whenGetInformationAboutFolder2() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));
        }

        @Test
        public void whenGetInformationAboutFolderWithIncorrectFolderName() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test3"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        public void whenGetInformationAboutFolderWithIncorrectParentFolder() throws Exception {
            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test3"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));
        }

        @Test
        public void whenGetInformationAboutFolderWithIncorrectFileInRoot() throws Exception {
            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        public void whenTryToFindWithIncorrectFileFolderName() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test2/test3"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        public void whenTryToFindSuccess() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test2/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));
        }

        @Test
        public void whenTryToFindInRootFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));
        }

        @Test
        public void whenTryToFindFile() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/123"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/123/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.size").value("Hello, World".getBytes().length));


            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/123/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/123/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()))
                    .andExpect(jsonPath("$.size").value(file.getBytes().length));
        }

        @Test
        public void whenTryToFindFileAndFailed() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/123/123"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/123/123/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.size").value(file.getBytes().length));


            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/123/test.txt"))
                    .andExpect(status().isNotFound());
        }

    }

    @Nested
    @DisplayName("Проверка метода удаления ресурса")
    class DeleteFolderOrFile {

        @Test
        void whenDeleteFolderIsSuccessful() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(delete(deleteApi)
                            .param("path", "test/"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));
        }

        @Test
        void whenDeleteFolderIsSuccessThenIncludeFoldersAreDeleted() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/qwe"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/qwe/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test2"));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test3"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test3"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test3/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test3"));

            mockMvc.perform(delete(deleteApi)
                            .param("path", "test/"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test2/"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test3/"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/qwe/test.txt"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Родительская папка не существует"));
        }

        @Test
        void whenDeleteFileIsSuccessful() throws Exception {

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()))
                    .andExpect(jsonPath("$.size").value(file.getBytes().length));

            mockMvc.perform(delete(deleteApi)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));
        }
    }

    @Nested
    @DisplayName("Проверка метода загрузки файла на сервер")
    class UploadFileMethod {

        @Test
        void whenSuccessInRoot() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));
        }

        @Test
        void whenSuccessInFolder() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));
        }

        @Test
        void whenSuccessInFolder2() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));
        }

        @Test
        void whenSuccessInFolder3() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/qwe")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/qwe/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));
        }

        @Test
        void whenSuccessInFolderWithIncorrectPath() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/./qwe")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        void whenSuccessInFolderWithIncorrectFileType() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/fel.txt")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        void whenSuccessInFolderSuccessful() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"));
        }

        @Test
        void whenDublicateInOneFolder() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Ресурс по такому пути уже существует!"));


            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"));
        }

        @Test
        void whenDublicateInDifferentFoldersThenSuccess() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/qwe")
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/qwe/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));


            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test.txt"));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test/qwe/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/qwe/"))
                    .andExpect(jsonPath("$.name").value("test.txt"));
        }
    }

    @Nested
    @DisplayName("Проверка метода скачивания файла с сервера")
    class DownloadResourceMethod {

        @Test
        void whenDownloadFileIsSuccessfulThenReturnStream() throws Exception {
            byte[] expectedContent = file.getBytes();

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test"))
                    .andExpect(status().isCreated());

            MvcResult result = mockMvc.perform(get(downloadApi)
                            .param("path", "test/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andReturn();

            byte[] actualContent = result.getResponse().getContentAsByteArray();
            Assertions.assertArrayEquals(expectedContent, actualContent);
        }
    }

    @Nested
    @DisplayName("Проверка метода перемещения и переименования ресурса")
    class ReplaceResourceMethod {

        @Test
        @DisplayName("Проверка, что при переименовании ресурса все вложенные файлы тоже перемещаются")
        void whenRenameResourceIsSuccessful() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/test2/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(replaceApi)
                            .param("from", "test")
                            .param("to", "test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test2/test2"))
                    .andExpect(status().isConflict());

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2/test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test2/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2/test2/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test2/test2/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test2/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));
        }

        @Test
        @DisplayName("Проверка, что при перемещении ресурса во внутреннюю папку все вложенные файлы тоже перемещаются и папка удаляется")
        void whenRenameResourceInIncludeFolder() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/test2"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/test2/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test/test2/test3"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/test2/"))
                    .andExpect(jsonPath("$.name").value("test3"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test/test2/test3"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test/test2/test3/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(replaceApi)
                            .param("from", "test/test2")
                            .param("to", "/test2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test2"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "/test2"))
                    .andExpect(status().isConflict());

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder))
                    .andExpect(jsonPath("$.name").value("test"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test2/"))
                    .andExpect(jsonPath("$.name").value("test.txt"))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2/test3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test2/"))
                    .andExpect(jsonPath("$.name").value("test3"))
                    .andExpect(jsonPath("$.type").value(FileType.DIRECTORY.name()));

            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test2/test3/test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(rootFolder + "test2/test3/"))
                    .andExpect(jsonPath("$.name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$.type").value(FileType.FILE.name()));
        }
    }

    @Nested
    @DisplayName("Проверка метода поиска ресурсов по названию")
    class SearchResource {

        @Test
        void whenSearchResourceIsFailedByBadPathToUrl() throws Exception {
            mockMvc.perform(get("/as" + searchApi)
                            .param("query", "test..png"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Ресурс не найден. Возможно, введен некорректный адрес URL"));
        }

        @Test
        void whenSearchResourceIsFailedByBadPath1() throws Exception {
            mockMvc.perform(get(searchApi)
                            .param("query", "test..png"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        void whenSearchResourceIsFailedByBadPath2() throws Exception {
            mockMvc.perform(get(searchApi)
                            .param("query", "test/.png"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        void whenSearchResourceIsFailedByBadPath3() throws Exception {
            mockMvc.perform(get(searchApi)
                            .param("query", "test/png."))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Невалидный или отсутствующий путь к папке"));
        }

        @Test
        void whenSearchIsFailedByNotFound() throws Exception {
            mockMvc.perform(get(searchApi)
                            .param("query", "test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        void whenSearchFileIsFailedByNotFound() throws Exception {
            mockMvc.perform(get(searchApi)
                            .param("query", "test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        void whenSearchIsFoundInOnePlace() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get(searchApi)
                            .param("query", "test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.size()").value(1))
                    .andExpect(jsonPath("$[0].path").value(rootFolder + "test/"))
                    .andExpect(jsonPath("$[0].name").value(getNameFromPath(file.getOriginalFilename())))
                    .andExpect(jsonPath("$[0].size").value(file.getSize()))
                    .andExpect(jsonPath("$[0].type").value(FileType.FILE.name()));
        }

        @Test
        void whenSearchIsFoundInTwoPlace() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "test"))
                    .andExpect(status().isCreated());

            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .param("path", "/"))
                    .andExpect(status().isCreated());

            mockMvc.perform(get(searchApi)
                            .param("query", "test.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.size()").value(2));
        }
    }

    @Nested
    @DisplayName("Проверка методов под неавторизованным пользователем")
    class WithAnonumUser {

        @BeforeEach
        void setUp() {
            SecurityContextHolder.getContext().setAuthentication(null);
        }

        @Test
        void whenMakeFolderWithoutUser() throws Exception {
            mockMvc.perform(post(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenGetUploadWithoutUser() throws Exception {
            mockMvc.perform(get(uploadOrGetInformation)
                            .param("path", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenPostUploadWithoutUser() throws Exception {
            mockMvc.perform(multipart(uploadOrGetInformation)
                            .file(file)
                            .contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenDeleteWithoutUser() throws Exception {
            mockMvc.perform(delete(deleteApi)
                            .param("path", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenDownloadWithoutUser() throws Exception {
            mockMvc.perform(get(downloadApi)
                            .param("path", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenGetInformationAboutResourceWithoutUser() throws Exception {
            mockMvc.perform(get(makeEmptyFolderOrGetInformationAboutFolder)
                            .param("path", "test"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenReplaceWithoutUser() throws Exception {
            mockMvc.perform(get(replaceApi)
                            .param("from", "test")
                            .param("to", "test2"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void whenSearchWithoutUser() throws Exception {
            mockMvc.perform(get(searchApi)
                            .param("query", "test"))
                    .andExpect(status().isUnauthorized());
        }
    }
}