package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvSyntaxErrorTest {
    @TempDir
    Path temp;

    @Test
    void reportsUnexpectedQuoteAtLogicalSourceRecord() throws Exception {
        assertFailure("old_codes,replacement\n1,ok\n2,ab\"c\n", "Unexpected quote inside unquoted CSV field", "source record 2");
    }

    @Test
    void reportsUnterminatedQuoteAtLogicalSourceRecord() throws Exception {
        assertFailure("old_codes,replacement\n1,ok\n2,\"never closes\n", "Unterminated quoted CSV field", "source record 2");
    }

    @Test
    void reportsRawLimitAndLogicalSourceRecord() throws Exception {
        assertFailure("old_codes,replacement\n1,ok\n2,abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\n", "CSV record exceeds configured raw character limit", "source record 2", "30");
    }

    @Test
    void preservesWrongColumnCountNumbering() throws Exception {
        assertFailure("old_codes,replacement\n1\n", "Wrong column count at source record 1");
    }

    @Test
    void reportsOversizedFirstDataRecord() throws Exception {
        assertFailure("old_codes,replacement\n1,abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\n",
                "CSV record exceeds configured raw character limit", "source record 1", "30");
    }

    @Test
    void identifiesMalformedHeaderAsHeader() throws Exception {
        assertFailure("old_codes,replac\"ement\n1,ok\n", "Unexpected quote inside unquoted CSV field", "in the header");
    }

    @Test
    void retainedBlankRecordIsIncluded() throws Exception {
        assertFailure(retainedBlueprint(), "old_codes,replacement\n1,ok\n\n2,ab\"c\n",
                "Unexpected quote inside unquoted CSV field", "source record 3");
    }

    @Test
    void retainedBlankWithCrLfIsIncludedOnce() throws Exception {
        assertFailure(retainedBlueprint(), "old_codes,replacement\r\n1,ok\r\n\r\n2,ab\"c\r\n",
                "Unexpected quote inside unquoted CSV field", "source record 3");
    }

    @Test
    void ignoredBlankRecordDoesNotChangeSyntaxRecordNumber() throws Exception {
        assertFailure("old_codes,replacement\n1,ok\n\n2,ab\"c\n", "Unexpected quote inside unquoted CSV field", "source record 2");
    }

    @Test
    void multilineFieldCountsAsOneLogicalRecord() throws Exception {
        assertFailure("old_codes,replacement\n\"1,\nfoo\",ok\n\n2,ab\"c\n",
                "Unexpected quote inside unquoted CSV field", "source record 2");
    }

    @Test
    void strictReaderValidatesMultilineRecordsBeforeLaterSyntaxError() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            Class<?> type = Class.forName("com.example.csvbatch.normalization.StrictCsvReader");
            var constructor = type.getDeclaredConstructor(java.io.Reader.class, char.class, char.class, int.class);
            constructor.setAccessible(true);
            var strict = (java.io.Reader) constructor.newInstance(new java.io.StringReader("\"a,\nb\",ok\n\n2,ab\"c\n"), ',', '\"', 128);
            char[] buffer = new char[64];
            while (strict.read(buffer, 0, buffer.length) != -1) { }
        }).hasMessageContaining("Unexpected quote inside unquoted CSV field");
    }

    @Test
    void numbersSyntaxRecordsWithoutAHeader() throws Exception {
        assertFailure(Path.of("examples/positional.yaml"), "a,b,c\nq,ab\"c\n", "Unexpected quote inside unquoted CSV field", "source record 2");
    }

    private void assertFailure(String content, String... expected) throws Exception {
        assertFailure(Path.of("examples/split-list.yaml"), content, expected);
    }

    private void assertFailure(Path blueprintPath, String content, String... expected) throws Exception {
        Path csv = temp.resolve("input.csv");
        Files.writeString(csv, content);
        var blueprint = new BlueprintLoader(config()).load(blueprintPath);
        var reader = new SourceCommandReader(csv, blueprint, config());
        assertThatThrownBy(() -> {
            reader.open(new ExecutionContext());
            reader.read();
            reader.read();
        }).satisfies(ex -> {
            for (String expectation : expected) {
                org.assertj.core.api.Assertions.assertThat(ex).hasMessageContaining(expectation);
            }
        });
    }

    private Path retainedBlueprint() throws Exception {
        Path blueprint = temp.resolve("retained.yaml");
        Files.writeString(blueprint, """
                version: 1
                name: retained
                input:
                  encoding: UTF-8
                  delimiter: ","
                  quote: '"'
                  escapeMode: doubled-quote
                  header: true
                  ignoreEmptyRecords: false
                mappings:
                  - name: replace-customer
                    oldIdValues:
                      source: {column: old_codes}
                      split: {delimiter: "|", mode: literal}
                      transforms: [trim]
                      dropEmpty: true
                      distinct: true
                    newIdValue:
                      source: {column: replacement}
                    metadataToUpdate:
                      source: {constant: customerId}
                """);
        return blueprint;
    }

    private AppProperties config() {
        return new AppProperties(false, temp, 2, 128, 8192, 30, 100, 16, 10000, 30, 3,
                "business_record", java.util.Map.of("customerId", new AppProperties.Property("customer_id", 64)));
    }
}
