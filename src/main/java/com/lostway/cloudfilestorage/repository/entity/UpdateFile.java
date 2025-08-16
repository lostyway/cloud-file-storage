package com.lostway.cloudfilestorage.repository.entity;

import com.lostway.jwtsecuritylib.kafka.enums.ContentType;
import com.lostway.jwtsecuritylib.kafka.enums.FileStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "uploaded_files")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFile {

    @Id
    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "full_path")
    private String fullPath;

    @Column(name = "jwt_token")
    private String jwtToken;

    @Column(name = "content_type")
    private ContentType contentType;

    @Column(name = "file_size")
    private long fileSize;

    @Column(name = "uploader_email")
    private String uploaderEmail;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private FileStatus status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
