package com.example.gradproj.EduNest.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.util.UUID;

/**
 * Universal Jackson Deserializer for UUID.
 *
 * Supports:
 * 1. Standard UUID strings (e.g. "550e8400-e29b-41d4-a716-446655440000").
 * 2. Numeric strings (e.g. "12") -> synthetic UUID(0L, 12L).
 * 3. Raw integer literals in JSON (e.g. {"id": 12}) -> synthetic UUID(0L, 12L).
 */
@JsonComponent
public class JacksonUuidDeserializer extends JsonDeserializer<UUID> {

    @Override
    public UUID deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ex) {
                try {
                    long num = Long.parseLong(text);
                    return new UUID(0L, num);
                } catch (NumberFormatException nfe) {
                    throw ctxt.weirdStringException(text, UUID.class, "Invalid UUID format: " + text);
                }
            }
        } else if (token == JsonToken.VALUE_NUMBER_INT) {
            long num = p.getLongValue();
            return new UUID(0L, num);
        }
        return (UUID) ctxt.handleUnexpectedToken(UUID.class, p);
    }
}
