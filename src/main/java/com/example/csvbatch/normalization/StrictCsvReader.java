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
    private final boolean ignoreEmptyRecords;
    private int recordChars;
    private long completedRecords;
    private boolean currentRecordHasContent;
    private boolean carriageReturnSeen;

    StrictCsvReader(Reader reader, char delimiter, char quote, int maxRecordChars) {
        this(reader, delimiter, quote, maxRecordChars, true);
    }

    StrictCsvReader(Reader reader, char delimiter, char quote, int maxRecordChars,
                    boolean ignoreEmptyRecords) {
        super(reader);
        this.delimiter = delimiter;
        this.quote = quote;
        this.maxRecordChars = maxRecordChars;
        this.ignoreEmptyRecords = ignoreEmptyRecords;
    }

    @Override
    public int read() throws IOException {
        int c = in.read();
        check(c);
        return c;
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        int count = 0;
        while (count < length) {
            int c = in.read();
            if (c == -1) {
                check(-1);
                break;
            }
            long recordsBefore = completedRecords;
            boolean crlfEnd = carriageReturnSeen && c == '\n';
            check(c);
            buffer[offset + count++] = (char) c;
            // Include the LF of CRLF in this record, but do not read another row.
            if (crlfEnd || (completedRecords != recordsBefore && c != '\r')) break;
        }
        return count == 0 ? -1 : count;
    }

    long recordsSeen() {
        return completedRecords + (currentRecordHasContent ? 1 : 0);
    }

    long failedRecord() {
        return completedRecords + 1;
    }

    private void check(int c) throws IOException {
        if (c == -1) {
            if (state == State.QUOTED) throw new IOException("Unterminated quoted CSV field");
            return;
        }
        if (++recordChars > maxRecordChars) {
            throw new IOException("CSV record exceeds configured raw character limit " + maxRecordChars);
        }
        if (carriageReturnSeen && c == '\n') {
            carriageReturnSeen = false;
            recordChars = 0;
            return;
        }
        carriageReturnSeen = false;
        if (c != '\r' && c != '\n') currentRecordHasContent = true;
        if (state == State.QUOTED) {
            if (c == quote) state = State.AFTER_QUOTE;
            return;
        }
        if (state == State.AFTER_QUOTE && c == quote) {
            state = State.QUOTED;
            return;
        }
        if (c == '\r' || c == '\n') {
            if (currentRecordHasContent || !ignoreEmptyRecords) completedRecords++;
            currentRecordHasContent = false;
            state = State.START;
            recordChars = 0;
            carriageReturnSeen = c == '\r';
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
