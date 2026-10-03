package com.example.csvbatch.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only reporting mapping; large command payload columns are intentionally unmapped.
 */
@Entity(name = "StagedCommand")
@Table(name = "staged_command")
@Immutable
public class StagedCommandEntity {
    @Id
    @Column(name = "command_id")
    private Long id;

    @Column(name = "submission_id", nullable = false, length = 80)
    private String submissionId;

    @Column(name = "source_record", nullable = false)
    private long sourceRecord;

    protected StagedCommandEntity() {
    }
}
