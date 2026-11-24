package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderPathTest {
    @Test
    void namesEmptySourceCsvInOpenFailure() throws Exception {
        Path path = Path.of("target/empty.csv");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "");
        var blueprint = new BlueprintLoader(BlueprintTest.config(Path.of("target")))
                .load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, BlueprintTest.config(Path.of("target")));

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: target/empty.csv");
    }

    @Test
    void namesMissingSourceCsvInOpenFailure() throws Exception {
        Path path = Path.of("target/missing-source.csv");
        Files.deleteIfExists(path);
        var blueprint = new BlueprintLoader(BlueprintTest.config(Path.of("target")))
                .load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, BlueprintTest.config(Path.of("target")));

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: target/missing-source.csv");
    }

    @Test
    void namesSourceCsvWhenCheckpointCannotBeRestored() throws Exception {
        Path path = Path.of("target/shortened-source.csv");
        Files.writeString(path, "old_codes,replacement\\n");
        var blueprint = new BlueprintLoader(BlueprintTest.config(Path.of("target")))
                .load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, BlueprintTest.config(Path.of("target")));
        var context = new ExecutionContext();
        context.putLong("source.records", 1L);

        assertThatThrownBy(() -> reader.open(context))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: target/shortened-source.csv");
    }
}
