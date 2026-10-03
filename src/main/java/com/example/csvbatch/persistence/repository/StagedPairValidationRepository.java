package com.example.csvbatch.persistence.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Submission-wide relationship checks, excluding deliberate no-op replacements. */
@Repository
public class StagedPairValidationRepository {
    public record Violation(long sourceRecord, String propertyKey) {
    }

    private final JdbcTemplate jdbc;

    public StagedPairValidationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Violation> findConflictingDestination(String submissionId) {
        return findViolation("""
                SELECT MIN(source_record), property_key FROM staged_pair
                WHERE submission_id = ? AND old_value <> new_value
                GROUP BY property_key, old_value
                HAVING MIN(new_value) <> MAX(new_value)
                FETCH FIRST 1 ROW ONLY
                """, submissionId);
    }

    public Optional<Violation> findChainOrCycle(String submissionId) {
        return findViolation("""
                SELECT p.source_record, p.property_key FROM staged_pair p
                WHERE p.submission_id = ? AND p.old_value <> p.new_value
                AND EXISTS (SELECT 1 FROM staged_pair q
                  WHERE q.submission_id = p.submission_id AND q.property_key = p.property_key
                  AND q.old_value = p.new_value AND q.old_value <> q.new_value)
                FETCH FIRST 1 ROW ONLY
                """, submissionId);
    }

    private Optional<Violation> findViolation(String sql, String submissionId) {
        return jdbc.query(sql, (rs, n) -> new Violation(rs.getLong(1), rs.getString(2)), submissionId)
                .stream().findFirst();
    }
}
