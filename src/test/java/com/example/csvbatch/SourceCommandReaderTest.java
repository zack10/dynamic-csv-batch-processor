package com.example.csvbatch;

import com.example.csvbatch.blueprint.Blueprint;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderTest {
    private static final AppProperties LIMITS = new AppProperties(false, Path.of("target"), 2, 128, 8192, 65536,
            100, 16, 10000, 30, 3,
            "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64)));

    private static final Blueprint HEADER_BLUEPRINT = new Blueprint(1, "test",
            new Blueprint.Input("UTF-8", ",", "\"", "double", true, "exact", false), List.of());

    @Test
    void namesEmptyCsvPathAndPreservesMissingHeaderCause() throws Exception {
        Path path = Path.of("target/source-command-reader-empty.csv");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "");

        SourceCommandReader reader = new SourceCommandReader(path, HEADER_BLUEPRINT, LIMITS);
        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Source CSV is empty: " + path)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Missing CSV header");
    }

    @Test
    void namesMissingCsvPathAndPreservesOpenCause() {
        Path path = Path.of("target/source-command-reader-missing.csv");

        SourceCommandReader reader = new SourceCommandReader(path, HEADER_BLUEPRINT, LIMITS);
        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + path)
                .hasCauseInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void namesMalformedCsvPathAndPreservesParsingCause() throws Exception {
        Path path = Path.of("target/source-command-reader-malformed.csv");
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{(byte) 0xC3, 0x28});

        SourceCommandReader reader = new SourceCommandReader(path, HEADER_BLUEPRINT, LIMITS);
        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + path)
                .hasCauseInstanceOf(java.nio.charset.CharacterCodingException.class);
    }
}
