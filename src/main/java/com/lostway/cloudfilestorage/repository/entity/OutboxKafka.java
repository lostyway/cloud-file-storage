package com.lostway.cloudfilestorage.repository.entity;

import com.lostway.cloudfilestorage.mapper.JsonConverter;
import com.lostway.jwtsecuritylib.kafka.FileUploadedEvent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_kafka")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxKafka {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jwt_token")
    private String jwtToken;

    @Column(name = "file_id")
    private UUID fileId;

    @Convert(converter = JsonConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private FileUploadedEvent payload;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "processed")
    private boolean processed;
}
