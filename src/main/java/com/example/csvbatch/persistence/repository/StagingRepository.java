package com.example.csvbatch.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persists both staging representations in the caller's chunk transaction.
 */
@Repository
public class StagingRepository {
    private final JdbcTemplate jdbc;

    public StagingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertBatch(List<Object[]> commands, List<Object[]> pairs) {
        jdbc.batchUpdate("""
                INSERT INTO staged_command(submission_id, source_record, mapping_index, mapping_name,
                  property_key, old_values, new_value) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, commands);
        jdbc.batchUpdate("INSERT INTO staged_pair VALUES (?, ?, ?, ?, ?, ?, ?)", pairs);
    }
}
