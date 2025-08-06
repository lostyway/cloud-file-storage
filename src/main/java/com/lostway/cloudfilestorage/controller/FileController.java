package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.StorageAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageResourceDTO;
import com.lostway.cloudfilestorage.exception.dto.ErrorResponseDTO;
import com.lostway.cloudfilestorage.minio.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;

@SecurityScheme(
        name = "sessionAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "JSESSIONID"
)
@Tag(name = "Cloud File Storage", description = "API для управления ресурсами (файлами/папками).")
@RestController
@RequestMapping("${api.url}")
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

    @Operation(
            summary = "Получение информации о ресурсе.",
            description = "Получает информацию о папке/файле (файл отображает его размер)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешное получение информации о ресурсе.",
                    content = @Content(schema = @Schema(implementation = StorageResourceDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ресурс не найден.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @GetMapping("/resource")
    public ResponseEntity<StorageResourceDTO> getInformationAboutResource(@RequestParam(name = "path") String path) {
        fileStorageService.createUserRootFolder();
        StorageResourceDTO result = fileStorageService.getInformationAboutResource(path);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Создание пустой директории.",
            description = "Пустая папка создаться только если есть родительская папка. " +
                    "При условии test будет создана папка rootFolder/test, но при создании папки test/test2 если папки " +
                    "test не существует --> будет ошибка со статусом 404"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Директория создана",
                    content = @Content(schema = @Schema(implementation = StorageFolderAnswerDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Родительская папка не существует.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Папка уже существует.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
    })
    @PostMapping("/directory")
    public ResponseEntity<StorageFolderAnswerDTO> createEmptyDirectory(@RequestParam(name = "path") String path) {
        fileStorageService.createUserRootFolder();
        StorageFolderAnswerDTO result = fileStorageService.createEmptyFolder(path);
        return ResponseEntity.status(CREATED).body(result);
    }

    @Operation(
            summary = "Удаление ресурса.",
            description = "Удаляет ресурс (всю папку, если нет формата), а если есть формат файла, то удалит именно файл по пути."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Успешное удаление ресурса (нет содержимого в ответе)."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ресурс не найден.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResource(@RequestParam String path) {
        fileStorageService.createUserRootFolder();
        fileStorageService.delete(path);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Получение информации о ресурсах по пути.",
            description = "Получение информации о директории со всеми файлами и папками внутри/файле."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешное получение информации о содержимом пути.",
                    content = @Content(schema = @Schema(implementation = StorageResourceDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ресурс не найден.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @GetMapping("/directory")
    public ResponseEntity<List<StorageResourceDTO>> getDirectoryFiles(@RequestParam(value = "path", required = false) String path) {
        fileStorageService.createUserRootFolder();
        List<StorageResourceDTO> result = fileStorageService.getFilesFromDirectory(path);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Загрузка ресурса на сервис",
            description = "Загружает указанные ресурсы по пути. Если папок не существует -> метод создаст их сам."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Успешная загрузка ресурсов на облачный сервис.",
                    content = @Content(schema = @Schema(implementation = StorageAnswerDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ресурс не найден.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Файл уже существует.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @PostMapping(value = "/resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageAnswerDTO> uploadResource(@RequestParam(value = "path", required = false) String path,
                                                           @RequestParam("file") MultipartFile file) {
        fileStorageService.createUserRootFolder();
        StorageAnswerDTO result = fileStorageService.uploadFile(path, file);
        return ResponseEntity.status(CREATED).body(result);
    }

    @Operation(
            summary = "Скачивание ресурсов с сервиса.",
            description = "Скачивание файлов с сервиса. Загрузка может быть долгой, если файлы тяжелые." +
                    " Передается через буффер."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ссылка на скачивание",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Пользователь не авторизован.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ресурс не найден.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Неизвестная ошибка.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @GetMapping("/resource/download")
    public ResponseEntity<StreamingResponseBody> downloadResource(@RequestParam(required = false) String path,
                                                                  HttpServletResponse response) {
        fileStorageService.createUserRootFolder();
        return fileStorageService.downloadResource(path, response);
    }

//    @GetMapping("/resource/move")
//    public ResponseEntity<StorageResourceDTO> replaceResource(@RequestParam("from") String oldPath,
//                                                              @RequestParam("to") String newPath) {
//    }
}
