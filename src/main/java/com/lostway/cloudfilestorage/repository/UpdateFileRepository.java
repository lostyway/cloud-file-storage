package com.lostway.cloudfilestorage.repository;

import com.lostway.cloudfilestorage.repository.entity.UpdateFile;
import com.lostway.jwtsecuritylib.kafka.enums.FileStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UpdateFileRepository extends JpaRepository<UpdateFile, UUID> {
    Optional<UpdateFile> findByFileId(UUID fileId);

    Optional<UpdateFile> findByFullPathAndUploaderEmail(String fullPath, String email);

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UpdateFile u WHERE u.createdAt <= :createdAt AND u.status IN :statuses")
    List<UpdateFile> findAllByStatusInAndCreatedAtAfter(List<FileStatus> statuses, Instant createdAt);

    @Query("SELECT u.fullPath FROM UpdateFile u WHERE u.fileId = :fileId")
    String findFullPathByFileId(@Param("fileId") UUID fileId);
}
