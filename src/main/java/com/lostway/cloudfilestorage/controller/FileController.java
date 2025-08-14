package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.UploadFileResponseDTO;
import com.lostway.cloudfilestorage.exception.dto.ErrorResponseDTO;
import com.lostway.cloudfilestorage.minio.FileStorageService;
import com.lostway.cloudfilestorage.service.UpdateStatusService;
import com.lostway.jwtsecuritylib.kafka.FileStatusUpdatedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final UpdateStatusService updateStatusService;


    @Operation(
            summary = "Загрузка документа",
            description = "Загружает документ pdf/docx"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Успешная загрузка документа.",
                    content = @Content(schema = @Schema(implementation = UploadFileResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Ошибка сервера.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @PostMapping("/report")
    public ResponseEntity<UploadFileResponseDTO> getMe(
            @RequestParam(value = "file") MultipartFile file,
            HttpServletRequest request
    ) {
        fileStorageService.createUserRootFolder(request);
        var response = fileStorageService.uploadFile(file, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получение информации о статусе документа",
            description = "Получение статусов из FileStatus (ENUM)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Получение информации о статусе документа.",
                    content = @Content(schema = @Schema(implementation = FileStatusUpdatedEvent.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Ошибка сервера.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    @GetMapping("/status")
    public ResponseEntity<FileStatusUpdatedEvent> getActualStatus(@RequestParam("fileId") String fileId, HttpServletRequest request) {
        return ResponseEntity.ok(updateStatusService.getActualStatus(fileId, request));
        //todo поменять на статус
    }
}
