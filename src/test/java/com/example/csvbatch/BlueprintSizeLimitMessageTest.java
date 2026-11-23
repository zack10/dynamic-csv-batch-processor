package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintSizeLimitMessageTest {
    @TempDir
    Path temp;

    @Test
    void includesBlueprintPathWhenFileExceedsSizeLimit() throws Exception {
        Path path = temp.resolve("oversized-blueprint.yaml");
        Files.write(path, new byte[262145]);

        assertThatThrownBy(() -> loader().load(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Blueprint exceeds 256 KiB: " + path);
    }

    private BlueprintLoader loader() {
        return new BlueprintLoader(new AppProperties(false, temp, 2, 128, 8192, 65536,
                100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32))));
    }
}
