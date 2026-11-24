package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderTest {
    @TempDir
    Path temp;

    @Test
    void namesEmptySourceCsvInOpenError() throws Exception {
        Path path = Path.of("target/empty-source.csv");
        Files.createDirectories(path.getParent());
        Files.deleteIfExists(path);
        Files.createFile(path);
        var config = BlueprintTest.config(temp);
        var blueprint = new BlueprintLoader(config).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, config);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Source CSV is empty: target/empty-source.csv");
    }
}
