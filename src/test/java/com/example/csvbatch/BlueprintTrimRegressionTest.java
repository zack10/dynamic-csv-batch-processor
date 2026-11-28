package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.blueprint.MappingPlan;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintTrimRegressionTest {
    @TempDir Path temp;

    @Test
    void quotedCsvValueIsParsedThenTrimmedByMappingPlan() throws Exception {
        Path blueprintPath = temp.resolve("blueprint.yaml");
        Path csvPath = temp.resolve("input.csv");
        Files.writeString(blueprintPath, blueprint("{column: old, trim: true}", "{column: replacement}", "{constant: customerId}"));
        Files.writeString(csvPath, "old,replacement\n\" 101 \",900\n");
        var blueprint = new BlueprintLoader(config()).load(blueprintPath);
        var reader = new SourceCommandReader(csvPath, blueprint, config());
        reader.open(new ExecutionContext());
        try {
            assertThat(reader.read().getFirst().oldIdValues()).containsExactly("101");
        } finally {
            reader.close();
        }
        assertThat(new MappingPlan(blueprint, new String[]{"old", "replacement"}, config())
                .apply(new String[]{" 101 ", "900"}, 1).getFirst().oldIdValues()).containsExactly("101");
    }

    @Test
    void valueLevelTrimTransformStillUsesMappingPlanApply() throws Exception {
        Path path = temp.resolve("transform.yaml");
        Files.writeString(path, blueprint("{column: old}", "{column: replacement}", "{constant: customerId}")
                .replace("      source: {column: old}\n    newIdValue:",
                        "      source: {column: old}\n      transforms: [trim]\n    newIdValue:"));
        var blueprint = new BlueprintLoader(config()).load(path);
        assertThat(new MappingPlan(blueprint, new String[]{"old", "replacement"}, config())
                .apply(new String[]{" 101 ", "900"}, 1).getFirst().oldIdValues()).containsExactly("101");
    }

    private AppProperties config() {
        return new AppProperties(false, temp, 2, 128, 8192, 65536, 100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
    }

    private static String blueprint(String oldSource, String newSource, String propertySource) {
        return "version: 1\nname: test\ninput:\n  encoding: UTF-8\n  delimiter: ','\n  quote: '\"'\n  escapeMode: doubled-quote\n  header: true\n  ignoreEmptyRecords: false\nmappings:\n  - name: replace\n    oldIdValues:\n      source: " + oldSource + "\n    newIdValue:\n      source: " + newSource + "\n    metadataToUpdate:\n      source: " + propertySource + "\n";
    }
}
