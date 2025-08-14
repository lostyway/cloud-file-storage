package com.lostway.cloudfilestorage.repository;

import com.lostway.cloudfilestorage.repository.entity.UpdateFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UpdateFileRepository extends JpaRepository<UpdateFile, UUID> {
}
