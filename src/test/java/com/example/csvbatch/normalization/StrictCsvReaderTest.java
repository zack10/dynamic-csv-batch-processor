package com.example.csvbatch.normalization;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictCsvReaderTest {
    @Test
    void validatesSyntaxAfterMultilineAndIgnoredRecords() {
        StrictCsvReader reader = new StrictCsvReader(
                new StringReader("\"a,\nb\",ok\n\n2,ab\"c\n"), ',', '\"', 128);
        char[] buffer = new char[64];
        assertThatThrownBy(() -> {
            while (reader.read(buffer, 0, buffer.length) != -1) { }
        }).hasMessageContaining("Unexpected quote inside unquoted CSV field");
    }
}
