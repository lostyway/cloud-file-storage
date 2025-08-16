package com.lostway.cloudfilestorage.service;

import com.lostway.cloudfilestorage.controller.dto.ActualStatusResponseDTO;
import com.lostway.cloudfilestorage.exception.dto.FileStorageNotFoundException;
import com.lostway.cloudfilestorage.mapper.KafkaMapper;
import com.lostway.cloudfilestorage.repository.UpdateFileRepository;
import com.lostway.jwtsecuritylib.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpdateStatusService {
    private final UpdateFileRepository updateFileRepository;
    private final JwtUtil jwtUtil;
    private final KafkaMapper kafkaMapper;

    @Transactional(readOnly = true)
    public ActualStatusResponseDTO getActualStatus(String fileId, HttpServletRequest request) {
        String token = jwtUtil.getTokenFromHeader(request)
                .orElseThrow(() -> new JwtException("Токен JWT не был найден"));

        String email = jwtUtil.extractEmail(token);

        var status = updateFileRepository.findByFileId(UUID.fromString(fileId))
                .orElseThrow(() -> new FileStorageNotFoundException("Такой файл не был найден в системе"));

        var file = updateFileRepository.findByFullPathAndUploaderEmail(status.getFullPath(), email)
                .orElseThrow(() -> new FileStorageNotFoundException("Файл не был найден по id: %s".formatted(fileId)));

        return kafkaMapper.fromEntityToActualStatusResponseDTO(file);
    }

    public String getFileName(UUID fileId) {
        var found = updateFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("Файл не был найден"));
        return found.getFileName();
    }
}
