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
    void namesEmptySourceCsvPathInError() throws Exception {
        Path input = temp.resolve("empty.csv");
        Files.createFile(input);
        var limits = BlueprintTest.config(temp);
        var blueprint = new BlueprintLoader(limits).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(input, blueprint, limits);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + input);
    }
}
