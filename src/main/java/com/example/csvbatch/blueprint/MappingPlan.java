package com.example.csvbatch.blueprint;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.normalization.Command;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable plan: header resolution and operation selection happen only once.
 */
public final class MappingPlan {
    private record Compiled(String name, Function<String[], List<String>> old,
                            Function<String[], List<String>> next, Function<String[], List<String>> property) {
    }

    private final List<Compiled> mappings;
    private final AppProperties limits;

    public MappingPlan(Blueprint blueprint, String[] header, AppProperties limits) {
        this.limits = limits;
        Map<String, Integer> names = new HashMap<>();
        if (header != null) {
            for (int i = 0; i < header.length; i++) {
                if (header[i].isBlank() || names.put(header[i], i) != null) {
                    throw new IllegalArgumentException("Empty or duplicate header: " + header[i]);
                }
            }
        }
        mappings = blueprint.mappings().stream().map(m -> new Compiled(m.name(),
                compile(m.oldIdValues(), names), compile(m.newIdValue(), names), compile(m.metadataToUpdate(), names))).toList();
    }

    public List<Command> apply(String[] row, long record) {
        var result = new ArrayList<Command>(mappings.size());
        for (int i = 0; i < mappings.size(); i++) {
            var m = mappings.get(i);
            try {
                var old = m.old().apply(row);
                String next = m.next().apply(row).getFirst();
                String key = m.property().apply(row).getFirst();
                validate(old, next, key, limits);
                result.add(new Command(record, i, m.name(), old, next, key));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Source record " + record + ", mapping " + m.name() + ": " + ex.getMessage(), ex);
            }
        }
        return result;
    }

    public static void validate(List<String> old, String next, String key, AppProperties limits) {
        int max = limits.property(key).maxLength();
        if (old == null || old.isEmpty() || old.size() > limits.maxOldIds()) {
            throw new IllegalArgumentException("Old values must contain 1.." + limits.maxOldIds() + " IDs");
        }
        validateId(next, max);
        old.forEach(id -> validateId(id, max));
    }

    private static void validateId(String id, int max) {
        if (id == null || id.isBlank() || id.length() > max || id.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("ID is blank, contains NUL, or exceeds property max-length " + max);
        }
    }

    private static Function<String[], List<String>> compile(Blueprint.Value value, Map<String, Integer> header) {
        var source = value.source();
        List<Integer> indexes;
        if (source.column() != null) {
            indexes = List.of(resolve(source.column(), header));
        } else if (source.columns() != null) {
            indexes = source.columns().stream().map(s -> resolve(s, header)).toList();
        } else if (source.index() != null) {
            indexes = List.of(source.index());
        } else if (source.indexes() != null) {
            indexes = source.indexes();
        } else {
            indexes = List.of();
        }
        var transforms = value.transforms() == null ? List.<String>of() : value.transforms();
        Pattern split = value.split() == null ? null : Pattern.compile(Pattern.quote(value.split().delimiter()));
        return row -> {
            var values = new ArrayList<String>();
            if (source.constant() != null) {
                values.add(source.constant());
            } else for (int index : indexes) {
                if (index >= row.length) {
                    throw new IllegalArgumentException("Missing column index " + index);
                }
                values.add(row[index]);
            }
            if (split != null) values = new ArrayList<>(Arrays.asList(split.split(values.getFirst(), -1)));
            for (String transform : transforms) {
                values.replaceAll(s -> switch (transform) {
                    case "trim" -> s.strip();
                    case "uppercase" -> s.toUpperCase(Locale.ROOT);
                    case "lowercase" -> s.toLowerCase(Locale.ROOT);
                    default -> throw new IllegalStateException("Uncompiled transformation");
                });
            }
            if (Boolean.TRUE.equals(value.dropEmpty())) {
                values.removeIf(String::isEmpty);
            }
            if (Boolean.TRUE.equals(value.distinct())) {
                values = new ArrayList<>(values.stream().distinct().toList());
            }
            if (value.valueMap() != null) {
                values.replaceAll(s -> {
                    String mapped = value.valueMap().get(s);
                    if (mapped == null) throw new IllegalArgumentException("Unmapped property value: " + s);
                    return mapped;
                });
            }
            return values;
        };
    }

    private static int resolve(String name, Map<String, Integer> header) {
        Integer index = header.get(name);
        if (index == null) {
            throw new IllegalArgumentException("Missing header: " + name);
        }
        return index;
    }
}
