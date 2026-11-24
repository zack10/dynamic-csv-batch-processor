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

class SourceCommandReaderEmptyFileTest {
    @Test
    void saysSourceCsvIsEmptyAndNamesPath() throws Exception {
        var path = Files.createTempFile("empty-source", ".csv");
        var reader = new SourceCommandReader(path, blueprint(), limits());

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Source CSV is empty: " + path)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause()
                .hasMessage("Missing CSV header");
    }

    private static Blueprint blueprint() {
        return new Blueprint(1, "test", new Blueprint.Input("UTF-8", ",", "\"", "double", true,
                "exact", false), List.of());
    }

    private static AppProperties limits() {
        return new AppProperties(false, Path.of("target"), 2, 16, 128, 8192,
                100, 16, 10000, 30, 3, "business_record",
                Map.of("customerId", new AppProperties.Property("customer_id", 64)));
    }
}
