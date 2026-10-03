package com.example.csvbatch.execution;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.persistence.repository.TargetMetadataRepository;
import org.springframework.stereotype.Component;

import java.sql.Types;

@Component
public class TargetValidator {
    private final TargetMetadataRepository targets;
    private final AppProperties config;

    public TargetValidator(TargetMetadataRepository targets, AppProperties config) {
        this.targets = targets;
        this.config = config;
    }

    public void validate() throws Exception {
        String database = targets.databaseProductName();
        if (!database.equals("H2") && !database.equals("PostgreSQL")) {
            throw new IllegalArgumentException("Unsupported database");
        }
        if (database.equals("H2")) {
            var settings = targets.h2CollationSettings();
            if (settings.stream().anyMatch(s -> !s.equalsIgnoreCase("OFF"))) {
                throw new IllegalArgumentException("H2 requires default binary string comparison (COLLATION OFF)");
            }
        }
        for (var entry : config.properties().entrySet()) {
            var metadata = targets.propertyMetadata(entry.getKey());
            if (metadata.jdbcType() != Types.VARCHAR
                    || metadata.typeName().toUpperCase().contains("IGNORECASE")) {
                throw new IllegalArgumentException("Version 1 supports only case-sensitive VARCHAR/TEXT properties: " + entry.getKey());
            }
            if (metadata.precision() > 0 && metadata.precision() < entry.getValue().maxLength()) {
                throw new IllegalArgumentException("Configured max-length exceeds column size: " + entry.getKey());
            }
            if (database.equals("PostgreSQL")) {
                Boolean deterministic = targets.propertyCollationDeterministic(entry.getKey());
                if (!Boolean.TRUE.equals(deterministic)) {
                    throw new IllegalArgumentException("Nondeterministic property collation is unsupported");
                }
            }
        }
        if (database.equals("PostgreSQL")) {
            Boolean deterministic = targets.stagingCollationDeterministic();
            if (!Boolean.TRUE.equals(deterministic)) {
                throw new IllegalArgumentException("Staging requires deterministic collation");
            }
        }
    }
}
