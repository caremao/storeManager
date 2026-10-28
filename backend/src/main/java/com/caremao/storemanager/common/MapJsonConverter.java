package com.caremao.storemanager.common;

import java.util.Map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Guarda un Map como texto JSON (propiedades de items, detalles del historial). */
@Converter
public class MapJsonConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        return attribute == null ? null : MAPPER.writeValueAsString(attribute);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : MAPPER.readValue(dbData, TYPE);
    }
}
