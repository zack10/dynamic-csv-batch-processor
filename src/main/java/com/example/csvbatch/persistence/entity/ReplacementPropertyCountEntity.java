package com.example.csvbatch.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity(name = "ReplacementPropertyCount")
@Table(name = "replacement_property_count")
@IdClass(ReplacementPropertyCountEntity.Key.class)
public class ReplacementPropertyCountEntity {
    @Id @Column(name = "submission_id", length = 80)
    private String submissionId;
    @Id @Column(name = "property_key", length = 63)
    private String propertyKey;
    @Column(name = "updated_rows", nullable = false)
    private long updatedRows;

    protected ReplacementPropertyCountEntity() {}

    public record Key(String submissionId, String propertyKey) {}
}
