package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Applies replacement batches and records counters in the caller's chunk transaction.
 */
@Repository
public class ReplacementUpdateRepository {
    private final JdbcTemplate jdbc;
    private final AppProperties limits;

    public ReplacementUpdateRepository(DataSource dataSource, AppProperties limits) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(limits.statementTimeoutSeconds());
        this.limits = limits;
    }

    public void applyAndRecord(String submission, Map<String, List<Object[]>> groups) {
        long statements = 0, affected = 0, zero = 0, unknown = 0;
        for (var entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            // Identifiers originate exclusively from validated application configuration.
            String column = limits.property(entry.getKey()).column();
            int[] counts = jdbc.batchUpdate("UPDATE " + limits.targetTable() + " SET " + column
                    + " = ? WHERE " + column + " = ?", entry.getValue());
            for (int count : counts) {
                statements++;
                if (count == Statement.EXECUTE_FAILED) {
                    throw new IllegalStateException("Database reported a failed batch statement");
                } else if (count == Statement.SUCCESS_NO_INFO) {
                    unknown++;
                } else if (count == 0) {
                    zero++;
                } else {
                    affected += count;
                }
            }
        }
        // Database counters roll back with the business updates, including on retried chunks.
        jdbc.update("""
                UPDATE replacement_run SET sql_statements = sql_statements + ?, affected_rows = affected_rows + ?,
                zero_matches = zero_matches + ?, unknown_counts = unknown_counts + ? WHERE submission_id = ?
                """, statements, affected, zero, unknown, submission);
    }
}
