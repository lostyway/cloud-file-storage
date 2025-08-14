package com.lostway.cloudfilestorage.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostway.jwtsecuritylib.kafka.FileUploadedEvent;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class JsonConverter implements AttributeConverter<FileUploadedEvent, String> {
    public static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.findAndRegisterModules();
    }

    @Override
    public String convertToDatabaseColumn(FileUploadedEvent attribute) {
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Не удалось конвертировать FileUploadedEvent в JSON", e);
        }
    }

    @Override
    public FileUploadedEvent convertToEntityAttribute(String dbData) {
        try {
            return mapper.readValue(dbData, FileUploadedEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Не удалось конвертировать JSON в FileUploadedEvent", e);
        }
    }
}
