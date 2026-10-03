package com.example.csvbatch.normalization;

import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

public final class CanonicalCodec {
    public static final String HEADER = "old_id_values,new_id_value,metadata_to_update";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CanonicalCodec() {
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static List<String> oldValues(String json) {
        var node = JSON.readTree(json);
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("old_id_values must be a JSON array");
        }
        var result = new ArrayList<String>();
        node.forEach(value -> {
            if (!value.isString()) {
                throw new IllegalArgumentException("Every old ID must be a JSON string");
            }
            result.add(value.asString());
        });
        return result;
    }

    public static String field(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String row(Command command) {
        return field(json(command.oldIdValues())) + "," + field(command.newIdValue()) + "," + field(command.metadataToUpdate());
    }
}
