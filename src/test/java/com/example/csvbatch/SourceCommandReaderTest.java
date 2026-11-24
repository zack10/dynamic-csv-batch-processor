package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderTest {
    @TempDir
    Path temp;

    @Test
    void namesEmptyInputInOpenFailure() throws Exception {
        Path input = temp.resolve("empty.csv");
        Files.createFile(input);
        var blueprint = new BlueprintLoader(BlueprintTest.config(temp))
                .load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(input, blueprint, BlueprintTest.config(temp));

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Source CSV is empty: " + input)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(error -> assertThat(error.getCause()).hasMessage("Missing CSV header"));
    }
}
