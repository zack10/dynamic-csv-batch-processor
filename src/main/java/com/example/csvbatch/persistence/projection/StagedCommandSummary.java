package com.example.csvbatch.persistence.projection;

public record StagedCommandSummary(long sourceRecordsCommitted, long canonicalCommands) {
}
