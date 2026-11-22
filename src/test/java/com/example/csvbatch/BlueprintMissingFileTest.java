package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintMissingFileTest {
    @Test
    void namesMissingBlueprintPathInError() {
        var path = Path.of("target/missing-blueprint.yaml");
        var loader = new BlueprintLoader(new AppProperties(false, Path.of("target"), 2, 128, 8192, 65536,
                100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32))));

        assertThatThrownBy(() -> loader.load(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Blueprint file does not exist: target/missing-blueprint.yaml");
    }
}
