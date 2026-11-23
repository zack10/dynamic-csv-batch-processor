package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintSizeLimitTest {
    @TempDir
    Path temp;

    @Test
    void namesBlueprintThatExceedsSizeLimit() throws Exception {
        Path path = temp.resolve("oversized-blueprint.yaml");
        Files.write(path, new byte[262145]);
        var loader = loader();

        assertThatThrownBy(() -> loader.load(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Blueprint exceeds 256 KiB: " + path);
    }

    @Test
    void acceptsBlueprintAtSizeLimit() throws Exception {
        Path path = temp.resolve("boundary-blueprint.yaml");
        String blueprint = Files.readString(Path.of("examples/split-list.yaml"));
        int padding = 262144 - blueprint.getBytes(StandardCharsets.UTF_8).length;
        Files.writeString(path, blueprint + "#".repeat(padding), StandardCharsets.UTF_8);

        assertThatCode(() -> loader().load(path)).doesNotThrowAnyException();
    }

    private BlueprintLoader loader() {
        return new BlueprintLoader(new AppProperties(false, temp, 2, 128, 8192, 65536,
                100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32))));
    }
}
