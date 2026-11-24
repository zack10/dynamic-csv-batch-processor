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
    void emptySourceCsvNamesPathInOpenFailure() throws Exception {
        Path path = Path.of("target/empty-source.csv");
        Files.writeString(path, "");
        var config = BlueprintTest.config(Path.of("target"));
        var blueprint = new BlueprintLoader(config).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, config);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Source CSV is empty: target/empty-source.csv");
    }

    @Test
    void openFailureKeepsMessagePrefixAndNamesSourcePath() throws Exception {
        Path path = Path.of("target/open-failure.csv");
        Files.writeString(path, "old_codes,replacement\n");
        var config = BlueprintTest.config(Path.of("target"));
        var blueprint = new BlueprintLoader(config).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, config);
        var context = new ExecutionContext();
        context.putLong("source.records", 1L);

        assertThatThrownBy(() -> reader.open(context))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: target/open-failure.csv")
                .hasMessageStartingWith("Cannot open source CSV");
    }
}
