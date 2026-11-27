package com.example.csvbatch.normalization;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.blueprint.BlueprintLoader;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Reads and normalizes the complete source without persisting its commands. */
@Component
public final class SourceValidationTasklet {
    private final BlueprintLoader loader;
    private final AppProperties limits;

    public SourceValidationTasklet(BlueprintLoader loader, AppProperties limits) {
        this.loader = loader;
        this.limits = limits;
    }

    public void validate(Path input, Path blueprint) throws Exception {
        var reader = new SourceCommandReader(input, loader.load(blueprint), limits);
        try {
            reader.open(new ExecutionContext());
            while (reader.read() != null) {
                // MappingPlan performs all source-record validation while normalizing.
            }
        } finally {
            reader.close();
        }
    }
}
