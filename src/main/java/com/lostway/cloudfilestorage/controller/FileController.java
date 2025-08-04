package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.minio.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("${api.url}")
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

//    @GetMapping("/resource/{path}")
//    public String getInformationAboutResource(@PathVariable("path") String path) {
//        return "Path is: " + path;
//    }

    @PostMapping("/directory/")
    public ResponseEntity<StorageFolderAnswerDTO> createEmptyDirectory(@RequestParam String pathFolder) {
        StorageFolderAnswerDTO result = fileStorageService.createEmptyFolder(pathFolder);
        return ResponseEntity.status(CREATED).body(result);
    }
}
