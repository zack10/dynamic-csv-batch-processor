package com.example.csvbatch.normalization;

import java.util.List;

public record Command(long sourceRecord, int mappingIndex, String mappingName,
                      List<String> oldIdValues, String newIdValue, String metadataToUpdate) {
    public Command {
        oldIdValues = List.copyOf(oldIdValues);
    }
}
