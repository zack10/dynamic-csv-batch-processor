package com.example.csvbatch.normalization;

import com.example.csvbatch.AppProperties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import com.example.csvbatch.persistence.repository.StagingRepository;

public final class StagingWriter implements ItemWriter<List<Command>> {
    private final StagingRepository staging;
    private final String submission;
    private final AppProperties limits;

    public StagingWriter(StagingRepository staging, String submission, AppProperties limits) {
        this.staging = staging;
        this.submission = submission;
        this.limits = limits;
    }

    @Override
    public void write(Chunk<? extends List<Command>> chunk) {
        List<Object[]> commands = new ArrayList<>();
        List<Object[]> pairs = new ArrayList<>();
        for (var group : chunk)
            for (var c : group) {
                commands.add(new Object[]{submission, c.sourceRecord(), c.mappingIndex(), c.mappingName(),
                        c.metadataToUpdate(), CanonicalCodec.json(c.oldIdValues()), c.newIdValue()});
                for (int i = 0; i < c.oldIdValues().size(); i++) {
                    if (pairs.size() >= limits.maxExpandedPairsPerChunk()) {
                        throw new IllegalArgumentException("Expanded chunk exceeds pair limit; reduce app.chunk-size");
                    }
                    pairs.add(new Object[]{submission, c.sourceRecord(), c.mappingIndex(), i,
                            c.metadataToUpdate(), c.oldIdValues().get(i), c.newIdValue()});
                }
            }
        staging.insertBatch(commands, pairs);
    }
}
