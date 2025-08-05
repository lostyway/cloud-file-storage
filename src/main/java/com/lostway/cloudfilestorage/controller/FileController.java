package com.lostway.cloudfilestorage.controller;

import com.lostway.cloudfilestorage.controller.dto.StorageAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageFolderAnswerDTO;
import com.lostway.cloudfilestorage.controller.dto.StorageResourceDTO;
import com.lostway.cloudfilestorage.minio.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("${api.url}")
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

    @GetMapping("/resource/")
    public ResponseEntity<StorageResourceDTO> getInformationAboutResource(@RequestParam String path) {
        fileStorageService.createUserRootFolder();
        StorageResourceDTO result = fileStorageService.getInformationAboutResource(path);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/directory/")
    public ResponseEntity<StorageFolderAnswerDTO> createEmptyDirectory(@RequestParam String pathFolder) {
        fileStorageService.createUserRootFolder();
        StorageFolderAnswerDTO result = fileStorageService.createEmptyFolder(pathFolder);
        return ResponseEntity.status(CREATED).body(result);
    }

    @DeleteMapping("/resource/")
    public ResponseEntity<Void> deleteResource(@RequestParam String path) {
        fileStorageService.createUserRootFolder();
        fileStorageService.delete(path);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/resource/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageAnswerDTO> uploadResource(@RequestParam(value = "path", required = false) String path,
                                                           @RequestParam("file") MultipartFile file) {
        fileStorageService.createUserRootFolder();
        StorageAnswerDTO result = fileStorageService.uploadFile(path, file);
        return ResponseEntity.status(CREATED).body(result);
    }
}
