package com.example.csvbatch.execution;

import com.example.csvbatch.persistence.repository.StagedPairValidationRepository;
import com.example.csvbatch.persistence.repository.StagedPairValidationRepository.Violation;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GlobalValidator {
    private final StagedPairValidationRepository pairs;

    public GlobalValidator(StagedPairValidationRepository pairs) {
        this.pairs = pairs;
    }

    public void validate(String submission) {
        failIfFound(pairs.findConflictingDestination(submission), "Conflicting destinations for the same old value");
        failIfFound(pairs.findChainOrCycle(submission), "Replacement chains/cycles are unsupported in version 1");
    }

    private void failIfFound(Optional<Violation> violation, String error) {
        violation.ifPresent(found -> {
            throw new IllegalArgumentException(error + " at source record " + found.sourceRecord()
                    + ", property " + found.propertyKey());
        });
    }
}
