package com.example.csvbatch.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only mapping of counters committed by the JDBC batch writer.
 */
@Entity(name = "ReplacementRun")
@Table(name = "replacement_run")
@Immutable
public class ReplacementRunEntity {
    @Id
    @Column(name = "submission_id", length = 80)
    private String submissionId;

    @Column(name = "sql_statements", nullable = false)
    private long sqlStatements;

    @Column(name = "affected_rows", nullable = false)
    private long affectedRows;

    @Column(name = "zero_matches", nullable = false)
    private long zeroMatches;

    @Column(name = "unknown_counts", nullable = false)
    private long unknownCounts;

    protected ReplacementRunEntity() {
    }
}
