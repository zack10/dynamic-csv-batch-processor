package com.example.csvbatch.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StagedPairId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "submission_id", nullable = false, length = 80)
    private String submissionId;

    @Column(name = "source_record", nullable = false)
    private long sourceRecord;

    @Column(name = "mapping_index", nullable = false)
    private int mappingIndex;

    @Column(name = "pair_index", nullable = false)
    private int pairIndex;

    protected StagedPairId() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StagedPairId that)) {
            return false;
        }
        return sourceRecord == that.sourceRecord && mappingIndex == that.mappingIndex
                && pairIndex == that.pairIndex && Objects.equals(submissionId, that.submissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(submissionId, sourceRecord, mappingIndex, pairIndex);
    }
}
