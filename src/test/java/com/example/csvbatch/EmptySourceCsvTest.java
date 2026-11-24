package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmptySourceCsvTest {
    @TempDir
    Path temp;

    @Test
    void namesEmptySourceCsvPathInError() throws Exception {
        Path input = temp.resolve("empty.csv");
        Files.createFile(input);
        var limits = new AppProperties(false, temp, 2, 128, 8192, 65536, 100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
        var blueprint = new BlueprintLoader(limits).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(input, blueprint, limits);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(org.springframework.batch.infrastructure.item.ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + input);
    }
}
