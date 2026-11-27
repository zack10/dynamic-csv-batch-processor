package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.blueprint.MappingPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintTrimTest {
    @TempDir
    Path temp;

    private static AppProperties config(Path work) {
        return new AppProperties(false, work, 2, 128, 8192, 65536, 100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
    }

    @Test
    void trimsConfiguredSourceValue() throws Exception {
        String yaml = blueprint("{column: old, trim: true}", "{column: replacement}", "{constant: customerId}");
        Path path = temp.resolve("trim.yaml");
        Files.writeString(path, yaml);

        var blueprint = new BlueprintLoader(config(temp)).load(path);
        var plan = new MappingPlan(blueprint, new String[]{"old", "replacement"}, config(temp));

        assertThat(plan.apply(new String[]{" 101 ", "\t 900\t"}, 1).getFirst().oldIdValues())
                .containsExactly("101");
        assertThat(plan.apply(new String[]{" 101 ", "\t 900\t"}, 1).getFirst().newIdValue())
                .isEqualTo("\t 900\t");
    }

    @Test
    void trimsQuotedAndIndexedListSources() throws Exception {
        Path path = temp.resolve("list.yaml");
        Files.writeString(path, blueprint("{indexes: [0, 1], trim: true}", "{index: 2}", "{constant: customerId}"));

        var blueprint = new BlueprintLoader(config(temp)).load(path);
        var plan = new MappingPlan(blueprint, new String[]{"old_a", "old_b", "replacement"}, config(temp));

        assertThat(plan.apply(new String[]{" 101 ", "\t202\t", "900"}, 1).getFirst().oldIdValues())
                .containsExactly("101", "202");
    }

    @Test
    void preservesWhitespaceWhenSourceTrimIsAbsent() throws Exception {
        Path path = temp.resolve("unchanged.yaml");
        Files.writeString(path, blueprint("{column: old}", "{column: replacement}", "{constant: customerId}"));

        var blueprint = new BlueprintLoader(config(temp)).load(path);
        var plan = new MappingPlan(blueprint, new String[]{"old", "replacement"}, config(temp));

        assertThat(plan.apply(new String[]{" 101 ", " 900 "}, 1).getFirst().oldIdValues())
                .containsExactly(" 101 ");
    }

    @Test
    void rejectsConfiguredBlankSourceValueWithRecordContext() throws Exception {
        Path path = temp.resolve("blank.yaml");
        Files.writeString(path, blueprint("{columns: [old, other], trim: true}", "{constant: 900}", "{constant: customerId}"));

        var blueprint = new BlueprintLoader(config(temp)).load(path);
        var plan = new MappingPlan(blueprint, new String[]{"old", "other"}, config(temp));

        assertThatThrownBy(() -> plan.apply(new String[]{" \t ", "101"}, 1))
                .hasMessageContaining("Source record 1")
                .hasMessageContaining("mapping replace");
    }

    @Test
    void rejectsBlankIndexedListValueWithRecordContext() throws Exception {
        Path path = temp.resolve("blank-index.yaml");
        Files.writeString(path, blueprint("{indexes: [0, 1], trim: true}", "{constant: 900}", "{constant: customerId}"));

        var blueprint = new BlueprintLoader(config(temp)).load(path);
        var plan = new MappingPlan(blueprint, new String[]{"old", "other"}, config(temp));

        assertThatThrownBy(() -> plan.apply(new String[]{"101", "\t \t"}, 1))
                .hasMessageContaining("Source record 1")
                .hasMessageContaining("mapping replace");
    }

    private static String blueprint(String oldSource, String newSource, String propertySource) {
        return "version: 1\n"
                + "name: trim-test\n"
                + "input:\n"
                + "  encoding: UTF-8\n"
                + "  delimiter: ','\n"
                + "  quote: '\"'\n"
                + "  escapeMode: doubled-quote\n"
                + "  header: true\n"
                + "  ignoreEmptyRecords: false\n"
                + "mappings:\n"
                + "  - name: replace\n"
                + "    oldIdValues:\n"
                + "      source: " + oldSource + "\n"
                + "    newIdValue:\n"
                + "      source: " + newSource + "\n"
                + "    metadataToUpdate:\n"
                + "      source: " + propertySource + "\n";
    }
}
