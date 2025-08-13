package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.StorageResourceDTO;
import com.lostway.cloudfilestorage.controller.dto.UploadFileResponseDTO;
import com.lostway.cloudfilestorage.exception.dto.ErrorResponseDTO;
import com.lostway.cloudfilestorage.minio.FileStorageService;
import com.lostway.jwtsecuritylib.JwtUtil;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

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
    private final JwtUtil jwtUtil;

    @PostMapping("/report")
    public ResponseEntity<UploadFileResponseDTO> getMe(
            @RequestParam(value = "file") MultipartFile file,
            HttpServletRequest request
    ) {
        String token = jwtUtil.getTokenFromHeader(request)
                .orElseThrow(() -> new JwtException("Invalid token"));
        String email = jwtUtil.extractEmail(token);

        fileStorageService.uploadFile(file, request);

        //todo валидация и сохранение в minio
        return ResponseEntity.ok(new UploadFileResponseDTO("Ваш документ принят! Отчет будет направлен на почту", email));
    }

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
            )
    })
    @GetMapping("/resource")
    public ResponseEntity<StorageResourceDTO> getInformationAboutResource(@RequestParam(name = "path") String path, HttpServletRequest request) {
        fileStorageService.createUserRootFolder(request);
        return ResponseEntity.ok(fileStorageService.getInformationAboutResource(path, request));
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
            )
    })
    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResource(@RequestParam String path, HttpServletRequest request) {
        fileStorageService.createUserRootFolder(request);
        fileStorageService.delete(path, request);
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
            )
    })
    @GetMapping("/directory")
    public ResponseEntity<List<StorageResourceDTO>> getDirectoryFiles(@RequestParam(value = "path", required = false) String path, HttpServletRequest request) {
        fileStorageService.createUserRootFolder(request);
        return ResponseEntity.ok(fileStorageService.getFilesFromDirectory(path, request));
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
            )
    })
    @GetMapping("/resource/download")
    public ResponseEntity<StreamingResponseBody> downloadResource(@RequestParam(required = false) String path,
                                                                  HttpServletResponse response,
                                                                  HttpServletRequest request) {
        fileStorageService.createUserRootFolder(request);
        return fileStorageService.downloadResource(path, response, request);
    }

    @Operation(
            summary = "Поиск ресурса по всей системе"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешное отображение найденного ресурса",
                    content = @Content(schema = @Schema(implementation = StorageResourceDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидный или отсутствующий путь.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @GetMapping("/resource/search")
    public ResponseEntity<List<StorageResourceDTO>> searchResource(
            @Parameter(description = "Запрос на поиск", example = "test2") @RequestParam("query") String query, HttpServletRequest request) {
        fileStorageService.createUserRootFolder(request);
        return ResponseEntity.ok(fileStorageService.searchResource(request, query));
    }
}
