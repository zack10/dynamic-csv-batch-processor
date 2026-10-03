package com.example.csvbatch.blueprint;

import java.util.List;
import java.util.Map;

public record Blueprint(int version, String name, Input input, List<Mapping> mappings) {

    public record Input(String encoding, String delimiter, String quote, String escapeMode,
                        boolean header, String headerMatching, boolean ignoreEmptyRecords) {
    }

    public record Source(String column, Integer index, String constant, List<String> columns, List<Integer> indexes) {
    }

    public record Split(String delimiter, String mode) {
    }

    public record Value(Source source, Split split, List<String> transforms,
                        Boolean dropEmpty, Boolean distinct, Map<String, String> valueMap, String onUnmapped) {
    }

    public record Mapping(String name, Value oldIdValues, Value newIdValue, Value metadataToUpdate) {
    }
}
