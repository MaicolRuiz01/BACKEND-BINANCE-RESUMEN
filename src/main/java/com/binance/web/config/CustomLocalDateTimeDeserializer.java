package com.binance.web.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class CustomLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        long timestamp = parser.getLongValue();

        // ⚠️ Ajusta este valor según la unidad (segundos/milisegundos/microsegundos)
        // Aquí asumimos microsegundos como en tu caso
        if (timestamp > 1_000_000_000_000_000L) {
            timestamp = timestamp / 1000; // Convertir microsegundos a milisegundos
        }

        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota"));
    }
}
