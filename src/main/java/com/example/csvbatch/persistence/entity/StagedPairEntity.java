package com.example.csvbatch.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity(name = "StagedPair")
@Table(name = "staged_pair")
@Immutable
public class StagedPairEntity {
    @EmbeddedId
    private StagedPairId id;

    @Column(name = "property_key", nullable = false, length = 63)
    private String propertyKey;

    @Column(name = "old_value", nullable = false, length = 256)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 256)
    private String newValue;

    protected StagedPairEntity() {
    }
}
