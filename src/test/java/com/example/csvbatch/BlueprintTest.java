package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.BoundedCsv;
import com.example.csvbatch.normalization.CanonicalCodec;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintTest {
    @TempDir
    Path temp;

    static AppProperties config(Path work) {
        return new AppProperties(false, work, 2, 128, 8192, 65536, 100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
    }

    @Test
    void suppliedLayoutsNormalizeAsExpected() throws Exception {
        var loader = new BlueprintLoader(config(temp));
        for (String name : List.of("team-a", "split-list", "positional", "wide")) {
            var blueprint = loader.load(Path.of("examples/" + name + ".yaml"));
            var reader = new SourceCommandReader(Path.of("examples/" + name + ".csv"), blueprint, config(temp));
            reader.open(new ExecutionContext());
            try {
                var first = reader.read();
                assertThat(first.getFirst().newIdValue()).isEqualTo("900");
                assertThat(first.getFirst().oldIdValues()).contains("101");
                assertThat(first.getFirst().metadataToUpdate()).isEqualTo("customerId");
                if (name.equals("wide")) assertThat(first).hasSize(2);
            } finally {
                reader.close();
            }
        }
    }

    @Test
    void rejectsUnknownKeysDuplicateKeysAndInvalidSelectors() throws Exception {
        String yaml = Files.readString(Path.of("examples/split-list.yaml"));
        for (String invalid : List.of(yaml + "typo: true\n", yaml + "version: 1\n",
                yaml.replace("{column: old_codes}", "{column: old_codes, index: 0}"),
                yaml.replace("[trim]", "[executeSql]"), yaml.replace("customerId", "unapproved"),
                yaml.replace("header: true", "header: false"))) {
            Path path = temp.resolve("invalid.yaml");
            Files.writeString(path, invalid);
            assertThatThrownBy(() -> new BlueprintLoader(config(temp)).load(path)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void handlesBomMultilineQuotesLeadingZerosAndRestart() throws Exception {
        Path path = temp.resolve("input.csv");
        Files.writeString(path, "\uFEFFold_codes,replacement\r\n\"00\"\"1\nA\",009\r\n002,010\r\n");
        var blueprint = new BlueprintLoader(config(temp)).load(Path.of("examples/split-list.yaml"));
        var reader = new SourceCommandReader(path, blueprint, config(temp));
        var context = new ExecutionContext();
        reader.open(context);
        var first = reader.read().getFirst();
        assertThat(first.oldIdValues()).containsExactly("00\"1\nA");
        assertThat(first.newIdValue()).isEqualTo("009");
        assertThat(CanonicalCodec.oldValues(CanonicalCodec.json(first.oldIdValues()))).isEqualTo(first.oldIdValues());
        reader.update(context);
        reader.close();
        reader = new SourceCommandReader(path, blueprint, config(temp));
        reader.open(context);
        assertThat(reader.read().getFirst().oldIdValues()).containsExactly("002");
        assertThat(reader.read()).isNull();
        reader.close();
    }

    @Test
    void rejectsOversizedFieldsAndBadQuotes() throws Exception {
        var blueprint = new BlueprintLoader(config(temp)).load(Path.of("examples/split-list.yaml"));
        for (String content : List.of("x".repeat(8193) + ",next\n", "\"abc\"junk,next\n", "\"unterminated,next\n")) {
            Path path = temp.resolve("invalid.csv");
            Files.writeString(path, content);
            assertThatThrownBy(() -> {
                try (var parser = new BoundedCsv(path, blueprint.input(), config(temp))) {
                    parser.next();
                }
            }).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void rejectsNonStringCanonicalIds() {
        assertThatThrownBy(() -> CanonicalCodec.oldValues("[123]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalCodec.oldValues("null")).isInstanceOf(IllegalArgumentException.class);
    }
}
