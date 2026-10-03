package com.example.csvbatch.normalization;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Checks the strict doubled-quote grammar and raw record bound before parser allocation.
 * Some CSV parsers accept an unterminated quoted field at EOF; this application does not.
 */
final class StrictCsvReader extends FilterReader {
    private enum State {START, UNQUOTED, QUOTED, AFTER_QUOTE}

    private State state = State.START;
    private final char delimiter;
    private final char quote;
    private final int maxRecordChars;
    private int recordChars;

    StrictCsvReader(Reader reader, char delimiter, char quote, int maxRecordChars) {
        super(reader);
        this.delimiter = delimiter;
        this.quote = quote;
        this.maxRecordChars = maxRecordChars;
    }

    @Override
    public int read() throws IOException {
        int c = in.read();
        check(c);
        return c;
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        int count = in.read(buffer, offset, length);
        if (count == -1) check(-1);
        else for (int i = offset; i < offset + count; i++) check(buffer[i]);
        return count;
    }

    private void check(int c) throws IOException {
        if (c == -1) {
            if (state == State.QUOTED) throw new IOException("Unterminated quoted CSV field");
            return;
        }
        if (++recordChars > maxRecordChars) throw new IOException("CSV record exceeds configured raw character limit");
        if (state == State.QUOTED) {
            if (c == quote) state = State.AFTER_QUOTE;
            return;
        }
        if (state == State.AFTER_QUOTE && c == quote) {
            state = State.QUOTED;
            return;
        }
        if (c == '\r' || c == '\n') {
            state = State.START;
            recordChars = 0;
            return;
        }
        if (c == delimiter) {
            state = State.START;
            return;
        }
        if (state == State.AFTER_QUOTE) throw new IOException("Unexpected character after closing CSV quote");
        if (c == quote) {
            if (state != State.START) throw new IOException("Unexpected quote inside unquoted CSV field");
            state = State.QUOTED;
        } else state = State.UNQUOTED;
    }
}
