package com.example.csvbatch.execution;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.blueprint.Blueprint;
import com.example.csvbatch.blueprint.MappingPlan;
import com.example.csvbatch.normalization.BoundedCsv;
import com.example.csvbatch.normalization.CanonicalCodec;
import com.example.csvbatch.normalization.Command;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

import java.nio.file.Path;
import java.util.Arrays;

public final class CanonicalReader implements ItemStreamReader<Command> {
    private final Path directory;
    private final String submission;
    private final boolean apply;
    private final AppProperties limits;
    private BoundedCsv csv;
    private long record;

    public CanonicalReader(Path directory, String submission, boolean apply, AppProperties limits) {
        this.directory = directory;
        this.submission = submission;
        this.apply = apply;
        this.limits = limits;
    }

    @Override
    public void open(@NonNull ExecutionContext context) {
        try {
            Artifacts.verify(directory, submission);
            // Canonical JSON escaping may enlarge a list cell relative to source fields.
            var canonicalLimits = new AppProperties(false, directory, limits.chunkSize(), 3,
                    Math.max(limits.maxFieldChars(), limits.maxOldIds() * 1540 + 2),
                    Math.max(limits.maxRecordChars(), limits.maxOldIds() * 1540 + 1024),
                    limits.maxOldIds(), limits.maxMappings(), limits.maxExpandedPairsPerChunk(),
                    limits.statementTimeoutSeconds(), limits.maxDatabaseAttempts(), limits.targetTable(), limits.properties());
            csv = new BoundedCsv(directory.resolve("canonical.csv"),
                    new Blueprint.Input("UTF-8", ",", "\"", "doubled-quote", true, "exact", false), canonicalLimits);
            if (!Arrays.equals(csv.next(), CanonicalCodec.HEADER.split(","))) {
                throw new IllegalArgumentException("Wrong canonical header");
            }
            record = context.getLong("canonical.records", 0L);
            for (long i = 0; i < record; i++) {
                if (csv.next() == null) {
                    throw new IllegalArgumentException("Canonical file shortened");
                }
            }
        } catch (Exception ex) {
            close();
            throw new ItemStreamException("Cannot open canonical artifact", ex);
        }
    }

    @Override
    public Command read() {
        if (!apply) {
            return null;
        }
        String[] row = csv.next();
        if (row == null) {
            return null;
        }
        record++;
        if (row.length != 3) {
            throw new IllegalArgumentException("Malformed canonical record " + record);
        }
        var old = CanonicalCodec.oldValues(row[0]);
        MappingPlan.validate(old, row[1], row[2], limits);
        return new Command(record, 0, "canonical", old, row[1], row[2]);
    }

    @Override
    public void update(ExecutionContext context) {
        context.putLong("canonical.records", record);
    }

    @Override
    public void close() {
        if (csv != null) {
            try {
                csv.close();
            } catch (Exception ex) {
                throw new ItemStreamException("Cannot close canonical CSV", ex);
            } finally {
                csv = null;
            }
        }
    }
}
