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
    void namesEmptySourceCsvWhenHeaderCannotBeRead() throws Exception {
        Path path = Path.of("target/empty-source.csv");
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[0]);
        try {
            var limits = BlueprintTest.config(Path.of("target"));
            var blueprint = new BlueprintLoader(limits).load(Path.of("examples/split-list.yaml"));
            var reader = new SourceCommandReader(path, blueprint, limits);

            assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                    .isInstanceOf(ItemStreamException.class)
                    .hasMessage("Cannot open source CSV: target/empty-source.csv");
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
