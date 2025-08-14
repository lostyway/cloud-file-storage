package com.lostway.cloudfilestorage.repository;

import com.lostway.cloudfilestorage.repository.entity.OutboxKafka;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxKafkaRepository extends JpaRepository<OutboxKafka, Long> {

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT o FROM OutboxKafka o
            WHERE o.processed = false
            ORDER BY o.createdAt ASC
            """)
    List<OutboxKafka> getActualKafkaEventsToSend(Pageable pageable);

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT o FROM OutboxKafka o
            WHERE o.processed = true
            ORDER BY o.createdAt ASC
            """)
    List<OutboxKafka> getOldOutboxEvents(Pageable pageable);

    @Modifying
    @Query("""
            UPDATE OutboxKafka o
            SET o.processed = true
            WHERE o.id in :ids
            """)
    int markEventsAsProcessed(@Param("ids") List<Long> ids);
}
