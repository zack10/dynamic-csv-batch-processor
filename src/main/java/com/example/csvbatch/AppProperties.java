package com.example.csvbatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties("app")
public record AppProperties(
        boolean launch,
        Path workDirectory,
        int chunkSize,
        int maxColumns,
        int maxFieldChars,
        int maxRecordChars,
        int maxOldIds,
        int maxMappings,
        int maxExpandedPairsPerChunk,
        int statementTimeoutSeconds,
        int maxDatabaseAttempts,
        String targetTable,
        Map<String, Property> properties) {

    public record Property(String column, int maxLength) {
    }

    public AppProperties {
        if (chunkSize < 1 || maxColumns < 1
                || maxFieldChars < 1
                || maxRecordChars < 1
                || maxOldIds < 1
                || maxMappings < 1
                || maxExpandedPairsPerChunk < 1
                || statementTimeoutSeconds < 1
                || maxDatabaseAttempts < 1) {
            throw new IllegalArgumentException("All processing limits must be positive");
        }
        identifier(targetTable);
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("No approved properties");
        }
        properties.forEach((key, value) -> {
            identifier(key);
            identifier(value.column());
            if (value.maxLength() < 1 || value.maxLength() > 256) {
                throw new IllegalArgumentException("Property max-length must be 1..256: " + key);
            }
        });
        if (properties.values()
                .stream()
                .map(property -> property.column().toLowerCase(Locale.ROOT))
                .distinct()
                .count() != properties.size()) {
            throw new IllegalArgumentException("Each property must map to a different physical column");
        }
        properties = Map.copyOf(properties);
    }

    public Property property(String key) {
        var value = properties.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown property: " + key);
        return value;
    }

    private static void identifier(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid configured identifier: " + value);
        }
    }
}
