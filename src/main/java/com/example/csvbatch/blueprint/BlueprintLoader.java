package com.example.csvbatch.blueprint;

import com.example.csvbatch.AppProperties;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class BlueprintLoader {
    private final AppProperties limits;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    public BlueprintLoader(AppProperties limits) {
        this.limits = limits;
    }

    public Blueprint load(Path path) throws Exception {
        if (Files.size(path) > 262144) {
            throw new IllegalArgumentException("Blueprint exceeds 256 KiB");
        }
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(262144);
        Object raw;
        try (Reader reader = Files.newBufferedReader(path)) {
            raw = new Yaml(new SafeConstructor(options)).load(reader);
        }
        require(raw instanceof Map<?, ?> root && root.get("input") instanceof Map<?, ?>,
                "Blueprint must contain an input object");
        var input = (Map<?, ?>) ((Map<?, ?>) raw).get("input");
        require(input.containsKey("header") && input.get("header") instanceof Boolean
                        && input.containsKey("ignoreEmptyRecords") && input.get("ignoreEmptyRecords") instanceof Boolean,
                "input.header and input.ignoreEmptyRecords must be explicit booleans");
        Blueprint result = mapper.convertValue(raw, Blueprint.class);
        validate(result);
        return result;
    }

    public void validate(Blueprint blueprint) {
        require(blueprint != null && blueprint.version() == 1, "Only blueprint version 1 is supported");
        require(blueprint.name() != null && !blueprint.name().isBlank(), "Blueprint name is required");
        var input = blueprint.input();
        require(input != null, "input is required");
        require(input.encoding() != null && !input.encoding().isBlank(), "encoding is required");
        Charset.forName(input.encoding());
        require(input.delimiter() != null && input.delimiter().length() == 1, "delimiter must be one character");
        require(input.quote() != null && input.quote().length() == 1, "quote must be one character");
        require(!input.delimiter().equals(input.quote()), "delimiter and quote must differ");
        require(!"\r\n".contains(input.delimiter()) && !"\r\n".contains(input.quote()), "Invalid delimiter/quote");
        require("doubled-quote".equals(input.escapeMode()), "escapeMode must be doubled-quote");
        require(input.headerMatching() == null || "exact".equals(input.headerMatching()), "headerMatching must be exact");
        require(blueprint.mappings() != null && !blueprint.mappings().isEmpty()
                && blueprint.mappings().size() <= limits.maxMappings(), "Invalid mapping count");
        var names = new HashSet<String>();
        for (var mapping : blueprint.mappings()) {
            require(mapping != null && mapping.name() != null && mapping.name().matches("[A-Za-z0-9_-]{1,80}"), "Invalid mapping name");
            require(names.add(mapping.name()), "Duplicate mapping name: " + mapping.name());
            validateValue(mapping.oldIdValues(), true, false, input.header());
            validateValue(mapping.newIdValue(), false, false, input.header());
            validateValue(mapping.metadataToUpdate(), false, true, input.header());
            var property = mapping.metadataToUpdate();
            if (property.valueMap() != null) {
                property.valueMap().values().forEach(limits::property);
            } else if (property.source().constant() != null) {
                limits.property(property.source().constant());
            } else {
                throw new IllegalArgumentException("Invalid property source");
            }
        }
    }

    private void validateValue(Blueprint.Value value, boolean list, boolean property, boolean header) {
        require(value != null && value.source() != null, "Every value requires a source");
        var s = value.source();
        int selectors = (s.column() != null ? 1 : 0) + (s.index() != null ? 1 : 0)
                + (s.constant() != null ? 1 : 0) + (s.columns() != null ? 1 : 0) + (s.indexes() != null ? 1 : 0);
        require(selectors == 1, "A source must have exactly one selector");
        require(header || (s.column() == null && s.columns() == null), "Named selectors require header: true");
        require(list || (s.columns() == null && s.indexes() == null), "Scalar values cannot select multiple columns");
        if (s.index() != null) {
            require(s.index() >= 0 && s.index() < limits.maxColumns(), "Invalid zero-based index");
        }
        if (s.column() != null) {
            require(!s.column().isBlank(), "Column name is empty");
        }
        if (s.columns() != null) {
            require(!s.columns().isEmpty() && s.columns().size() <= limits.maxColumns()
                    && s.columns().stream().allMatch(x -> x != null && !x.isBlank()), "Invalid columns");
        }
        if (s.indexes() != null) {
            require(!s.indexes().isEmpty() && s.indexes().size() <= limits.maxColumns()
                    && s.indexes().stream().allMatch(x -> x != null && x >= 0 && x < limits.maxColumns()), "Invalid indexes");
        }
        if (value.split() != null) {
            require(list && s.columns() == null && s.indexes() == null, "split requires a single list source");
            require("literal".equals(value.split().mode()) && value.split().delimiter() != null
                    && !value.split().delimiter().isEmpty(), "split requires a nonempty literal delimiter");
        }
        require(list || (value.dropEmpty() == null && value.distinct() == null), "dropEmpty/distinct apply only to lists");
        require(property || (value.valueMap() == null && value.onUnmapped() == null), "valueMap applies only to metadataToUpdate");
        if (value.valueMap() != null) {
            require(!value.valueMap().isEmpty() && "error".equals(value.onUnmapped()), "valueMap requires onUnmapped: error");
        } else {
            require(value.onUnmapped() == null, "onUnmapped requires valueMap");
        }
        if (value.transforms() != null) {
            require(value.transforms().stream().allMatch(
                    List.of("trim", "uppercase", "lowercase")::contains), "Unknown transformation");
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
