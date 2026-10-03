package com.example.csvbatch.execution;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.normalization.Command;
import com.example.csvbatch.persistence.repository.ReplacementUpdateRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReplacementWriter implements ItemWriter<Command> {
    private final ReplacementUpdateRepository replacements;
    private final AppProperties limits;
    private final String submission;

    public ReplacementWriter(ReplacementUpdateRepository replacements, AppProperties limits, String submission) {
        this.replacements = replacements;
        this.limits = limits;
        this.submission = submission;
    }

    @Override
    public void write(Chunk<? extends Command> chunk) {
        Map<String, List<Object[]>> groups = new LinkedHashMap<>();
        int expanded = 0;
        for (var command : chunk) {
            String property = command.metadataToUpdate();
            limits.property(property); // Reject unknown properties before any updates.
            var args = groups.computeIfAbsent(property, ignored -> new ArrayList<>());
            for (String old : command.oldIdValues()) {
                if (old.equals(command.newIdValue())) continue;
                if (++expanded > limits.maxExpandedPairsPerChunk()) {
                    throw new IllegalArgumentException("Expanded update chunk exceeds limit");
                }
                args.add(new Object[]{command.newIdValue(), old});
            }
        }
        replacements.applyAndRecord(submission, groups);
    }
}
