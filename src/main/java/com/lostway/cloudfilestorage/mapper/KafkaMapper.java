package com.lostway.cloudfilestorage.mapper;

import com.lostway.cloudfilestorage.repository.entity.OutboxKafka;
import com.lostway.cloudfilestorage.repository.entity.UpdateFile;
import com.lostway.jwtsecuritylib.kafka.FileStatusUpdatedEvent;
import com.lostway.jwtsecuritylib.kafka.FileUploadedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface KafkaMapper {

    FileUploadedEvent fromEntityToFileUpdateEvent(UpdateFile updateFile);

    List<FileUploadedEvent> fromEntitiesToFileUpdateEvents(List<UpdateFile> updateFiles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fileId", source = "fileUploadedEvent.fileId")
    @Mapping(target = "payload", source = "fileUploadedEvent")
    @Mapping(target = "createdAt", source = "fileUploadedEvent.createdAt")
    @Mapping(target = "processed", constant = "false")
    OutboxKafka fromDtoToEntity(FileUploadedEvent fileUploadedEvent);


    @Mapping(target = "fileId", source = "payload.fileId")
    @Mapping(target = "fileName", source = "payload.fileName")
    @Mapping(target = "contentType", source = "payload.contentType")
    @Mapping(target = "fileSize", source = "payload.fileSize")
    @Mapping(target = "uploaderEmail", source = "payload.uploaderEmail")
    @Mapping(target = "status", source = "payload.status")
    @Mapping(target = "createdAt", source = "payload.createdAt")
    @Mapping(target = "updatedAt", source = "payload.updatedAt")
    FileUploadedEvent fromEntityToDto(OutboxKafka outboxKafka);

    List<FileUploadedEvent> fromEntitiesToEvents(List<OutboxKafka> eventsEntities);

    FileStatusUpdatedEvent fromEntitiesToEventUpdate(UpdateFile updateFile);
}
