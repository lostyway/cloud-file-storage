package com.lostway.cloudfilestorage.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lostway.cloudfilestorage.exception.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.attribute.UserPrincipalNotFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleBadCredentialsException(AuthenticationException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDTO("Неверный логин или пароль"));
    }

    @ExceptionHandler({FolderAlreadyExistsException.class, ResourceInStorageAlreadyExists.class})
    public ResponseEntity<ErrorResponseDTO> handleAlreadyExists(RuntimeException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleUserAlreadyExistsException(UserAlreadyExistsException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(e.getMessage()));
    }

    @ExceptionHandler({
            SimilarResourceException.class,
            FileUploadSizeException.class,
            IllegalArgumentException.class,
            ResourcesNotTheSameTypeException.class})
    public ResponseEntity<ErrorResponseDTO> handleBadRequests(RuntimeException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponseDTO> handleFileStorageException(FileStorageException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDTO("Ошибка в файловой системе"));
    }

    @ExceptionHandler(ParentFolderNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleParentFolderNotFoundException(ParentFolderNotFoundException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDTO("Родительская папка не существует"));
    }

    @ExceptionHandler(InvalidFolderPathException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidFolderPathException(InvalidFolderPathException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDTO("Невалидный или отсутствующий путь к папке"));
    }

    @ExceptionHandler({CantGetUserContextIdException.class, UserPrincipalNotFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleCantGetUserContextIdException(RuntimeException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponseDTO(e.getMessage()));
    }

    @ExceptionHandler({FileStorageNotFoundException.class, FolderNotFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleFileStorageNotFoundException(RuntimeException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDTO("Ресурс не найден"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDTO("Ошибка при вводе параметров"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoHandlerFoundException(NoResourceFoundException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDTO("Ресурс не найден. Возможно, введен некорректный адрес URL"));
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponseDTO> handleJsonProcessingException(JsonProcessingException e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleAllException(Exception e) {
        throwLogError(e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDTO("Неизвестная ошибка"));
    }

    private static void throwLogError(Exception e) {
        log.error("Произошла ошибка: {}", e.getMessage());
    }
}
