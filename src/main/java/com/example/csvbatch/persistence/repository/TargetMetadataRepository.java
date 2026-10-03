package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.AppProperties;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Database metadata and collation reads used by target validation.
 */
@Repository
public class TargetMetadataRepository {
    public record ColumnMetadata(int jdbcType, String typeName, int precision) {
    }

    private final JdbcTemplate jdbc;
    private final AppProperties config;

    public TargetMetadataRepository(JdbcTemplate jdbc, AppProperties config) {
        this.jdbc = jdbc;
        this.config = config;
    }

    public String databaseProductName() {
        return jdbc.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
    }

    public List<String> h2CollationSettings() {
        return jdbc.queryForList("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'COLLATION'",
                String.class);
    }

    public ColumnMetadata propertyMetadata(String property) {
        String column = config.property(property).column();
        // Identifiers originate exclusively from validated application configuration.
        return jdbc.query("SELECT " + column + " FROM " + config.targetTable() + " WHERE 1=0", result -> {
            var metadata = result.getMetaData();
            return new ColumnMetadata(metadata.getColumnType(1), metadata.getColumnTypeName(1), metadata.getPrecision(1));
        });
    }

    public Boolean propertyCollationDeterministic(String property) {
        return jdbc.queryForObject("""
                SELECT c.collisdeterministic FROM pg_attribute a JOIN pg_collation c ON c.oid = a.attcollation
                WHERE a.attrelid = CAST(? AS regclass) AND a.attname = ?
                """, Boolean.class, config.targetTable(), config.property(property).column());
    }

    public Boolean stagingCollationDeterministic() {
        return jdbc.queryForObject("""
                SELECT c.collisdeterministic FROM pg_attribute a JOIN pg_collation c ON c.oid = a.attcollation
                WHERE a.attrelid = 'staged_pair'::regclass AND a.attname = 'old_value'
                """, Boolean.class);
    }
}
