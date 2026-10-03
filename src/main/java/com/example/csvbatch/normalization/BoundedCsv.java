package com.example.csvbatch.normalization;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.blueprint.Blueprint;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.UnescapedQuoteHandling;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * No background prefetch. Per-field and column limits apply inside the parser.
 */
public final class BoundedCsv implements AutoCloseable {
    private final CsvParser parser;
    private final Reader reader;
    private final int maxRecordChars;

    public BoundedCsv(Path path, Blueprint.Input format, AppProperties limits) throws IOException {
        var settings = new CsvParserSettings();
        settings.getFormat().setDelimiter(format.delimiter().charAt(0));
        settings.getFormat().setQuote(format.quote().charAt(0));
        settings.getFormat().setQuoteEscape(format.quote().charAt(0));
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setReadInputOnSeparateThread(false);
        settings.setInputBufferSize(65536);
        settings.setMaxCharsPerColumn(limits.maxFieldChars());
        settings.setMaxColumns(limits.maxColumns());
        settings.setSkipEmptyLines(format.ignoreEmptyRecords());
        settings.setCommentProcessingEnabled(false);
        settings.setSkipBitsAsWhitespace(false);
        settings.setIgnoreLeadingWhitespaces(false);
        settings.setIgnoreTrailingWhitespaces(false);
        settings.setNormalizeLineEndingsWithinQuotes(false);
        settings.setUnescapedQuoteHandling(UnescapedQuoteHandling.RAISE_ERROR);
        settings.setNullValue("");
        settings.setEmptyValue("");
        settings.setErrorContentLength(0);
        var decoder = Charset.forName(format.encoding())
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        var input = new PushbackReader(new InputStreamReader(Files.newInputStream(path), decoder), 1);
        int first = input.read();
        if (first != -1 && first != '\uFEFF') {
            input.unread(first);
        }
        reader = new StrictCsvReader(input, format.delimiter().charAt(0), format.quote().charAt(0), limits.maxRecordChars());
        maxRecordChars = limits.maxRecordChars();
        parser = new CsvParser(settings);
        try {
            parser.beginParsing(reader);
        } catch (RuntimeException ex) {
            try {
                reader.close();
            } catch (IOException close) {
                ex.addSuppressed(close);
            }
            throw ex;
        }
    }

    public String[] next() {
        String[] row = parser.parseNext();
        if (row != null) {
            long size = 0;
            for (String field : row) size += field.length();
            if (size > maxRecordChars) {
                throw new IllegalArgumentException("Logical record exceeds configured character limit");
            }
        }
        return row;
    }

    @Override
    public void close() throws IOException {
        try {
            parser.stopParsing();
        } finally {
            reader.close();
        }
    }
}
