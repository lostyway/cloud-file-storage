package com.lostway.cloudfilestorage.repository;

import com.lostway.jwtsecuritylib.kafka.enums.ContentType;
import com.lostway.jwtsecuritylib.kafka.enums.FileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "content_type")
    private ContentType contentType;

    @Column(name = "file_size")
    private long fileSize;

    @Column(name = "uploader_email")
    private String uploaderEmail;

    @Column(name = "status")
    private FileStatus status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
