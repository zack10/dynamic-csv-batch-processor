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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

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
                assert first != null;
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
        var cases = List.of(
                yaml + "typo: true\n",
                yaml + "version: 1\n",
                yaml.replace("{column: old_codes}", "{column: old_codes, index: 0}"),
                yaml.replace("[trim]", "[executeSql]"),
                yaml.replace("customerId", "unapproved"),
                yaml.replace("header: true", "header: false"),
                yaml.replace("      source: {constant: customerId}", "      source: {column: attribute}"),
                yaml.replace("      source: {constant: customerId}", "      source: {index: 0}"),
                yaml.replace("      source: {column: old_codes}", "      source: {index: 5000}"),
                yaml.replace("name: replace-customer", "name: \"   \"")
        );
        for (int i = 0; i < cases.size(); i++) {
            String s = cases.get(i);
            Path path = temp.resolve("invalid" + i + ".yaml");
            Files.writeString(path, s);
            try {
                new BlueprintLoader(config(temp)).load(path);
                throw new AssertionError("Expected failure for case " + i);
            } catch (RuntimeException e) {
                // expected
            }
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
    void closesInputWhenInitialDecodingFailsAndPreservesCleanupFailure() throws Exception {
        var blueprint = new BlueprintLoader(config(temp)).load(Path.of("examples/split-list.yaml"));
        Path path = temp.resolve("invalid-encoding.csv");
        for (boolean failClose : List.of(false, true)) {
            boolean[] closed = {false};
            var closeFailure = new IOException("Cleanup failed");
            var input = new ByteArrayInputStream(new byte[]{(byte) 0xFF}) {
                @Override
                public void close() throws IOException {
                    closed[0] = true;
                    if (failClose) throw closeFailure;
                    super.close();
                }
            };
            try (var files = mockStatic(Files.class)) {
                files.when(() -> Files.newInputStream(path)).thenReturn(input);
                assertThatThrownBy(() -> new BoundedCsv(path, blueprint.input(), config(temp)))
                        .isInstanceOf(java.nio.charset.MalformedInputException.class)
                        .satisfies(ex -> assertThat(ex.getSuppressed())
                                .containsExactly(failClose ? new Throwable[]{closeFailure} : new Throwable[0]));
                assertThat(closed[0]).isTrue();
            }
        }
    }

    @Test
    void rejectsNonStringCanonicalIds() {
        assertThatThrownBy(() -> CanonicalCodec.oldValues("[123]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalCodec.oldValues("null")).isInstanceOf(IllegalArgumentException.class);
    }
}
