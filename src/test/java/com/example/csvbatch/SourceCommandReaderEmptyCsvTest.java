package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderEmptyCsvTest {
    @Test
    void namesEmptySourceCsvInOpenFailure() throws Exception {
        Path path = Path.of("target/empty.csv");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "");
        var limits = new AppProperties(false, Path.of("target"), 2, 128, 8192, 65536,
                100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
        var blueprint = new BlueprintLoader(limits).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, limits);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(org.springframework.batch.infrastructure.item.ItemStreamException.class)
                .hasMessage("Source CSV is empty: target/empty.csv")
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Missing CSV header");
    }
}
