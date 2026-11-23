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

class EmptyCsvTest {
    @TempDir
    Path temp;

    @Test
    void namesEmptyCsvPathAndPreservesMissingHeaderCause() throws Exception {
        Path path = Path.of("target/empty.csv");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "");
        var blueprint = new BlueprintLoader(BlueprintTest.config(temp)).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, BlueprintTest.config(temp));

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: target/empty.csv")
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> assertThat(ex.getCause()).hasMessage("Missing CSV header"));
    }

    @Test
    void stillReadsNonEmptyCsv() throws Exception {
        Path path = temp.resolve("input.csv");
        Files.writeString(path, "old_codes,replacement\n001,002\n");
        var blueprint = new BlueprintLoader(BlueprintTest.config(temp)).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, BlueprintTest.config(temp));

        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read()).isNotNull();
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }
}
