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

class SourceCommandReaderTest {
    @TempDir
    Path temp;

    @Test
    void namesEmptySourceCsvInOpenError() throws Exception {
        Path path = temp.resolve("empty-source.csv");
        Files.createFile(path);

        assertThatThrownBy(() -> reader(path).open(new ExecutionContext()))
                .hasMessage("Cannot open source CSV: " + path);
    }

    @Test
    void namesMissingSourceCsvInOpenError() throws Exception {
        Path path = temp.resolve("missing-source.csv");

        assertThatThrownBy(() -> reader(path).open(new ExecutionContext()))
                .hasMessage("Cannot open source CSV: " + path);
    }

    private SourceCommandReader reader(Path path) throws Exception {
        var limits = new AppProperties(false, temp, 2, 128, 8192, 65536, 100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
        var blueprint = new BlueprintLoader(limits).load(Path.of("examples/split-list.yaml"));
        return new SourceCommandReader(path, blueprint, limits);
    }
}
