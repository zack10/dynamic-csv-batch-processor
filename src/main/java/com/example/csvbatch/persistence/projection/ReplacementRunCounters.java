package com.example.csvbatch.persistence.projection;

public record ReplacementRunCounters(long sqlStatements, long affectedRows, long zeroMatches, long unknownCounts) {
}
