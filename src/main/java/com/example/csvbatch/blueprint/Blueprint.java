package com.example.csvbatch.blueprint;

import java.util.List;
import java.util.Map;

public record Blueprint(int version, String name, Input input, List<Mapping> mappings) {

    /** Trims only the source-level padding explicitly covered by the blueprint option. */
    public static String trimSourceValue(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == ' ' || value.charAt(start) == '\t')) start++;
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\t')) end--;
        return value.substring(start, end);
    }

    public record Input(String encoding, String delimiter, String quote, String escapeMode,
                        boolean header, String headerMatching, boolean ignoreEmptyRecords) {
    }

    public record Source(String column, Integer index, String constant, List<String> columns, List<Integer> indexes,
                        Boolean trim) {
    }

    public record Split(String delimiter, String mode) {
    }

    public record Value(Source source, Split split, List<String> transforms,
                        Boolean dropEmpty, Boolean distinct, Map<String, String> valueMap, String onUnmapped) {
    }

    public record Mapping(String name, Value oldIdValues, Value newIdValue, Value metadataToUpdate) {
    }
}
