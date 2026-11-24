package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderTest {
    @Test
    void namesEmptySourceCsvInOpenError() throws Exception {
        Path path = Path.of("target/empty.csv");
        Files.writeString(path, "");
        var limits = BlueprintTest.config(Path.of("target"));
        var blueprint = new BlueprintLoader(limits).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, limits);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: target/empty.csv")
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> assertThatThrownBy(() -> { throw ex.getCause(); })
                        .hasMessage("Missing CSV header"));
    }
}
