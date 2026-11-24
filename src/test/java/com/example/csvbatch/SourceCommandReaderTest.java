package com.example.csvbatch;

import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.normalization.SourceCommandReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCommandReaderTest {
    @TempDir
    Path temp;

    private AppProperties config() {
        return new AppProperties(false, temp, 2, 128, 8192, 65536, 100, 16, 10000, 30, 3,
                "business_record", Map.of("customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property("department_id", 32)));
    }

    private com.example.csvbatch.blueprint.Blueprint blueprint() throws Exception {
        return new BlueprintLoader(config()).load(Path.of("examples/split-list.yaml"));
    }

    @Test
    void emptyCsvErrorIdentifiesInputPath() throws Exception {
        Path input = temp.resolve("empty.csv");
        Files.createFile(input);
        SourceCommandReader reader = new SourceCommandReader(input, blueprint(), config());

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + input);
    }

    @Test
    void sourceOpenFailureIdentifiesInputAndPreservesCause() throws Exception {
        Path input = temp.resolve("malformed.csv");
        Files.write(input, new byte[]{(byte) 0xFF});
        SourceCommandReader reader = new SourceCommandReader(input, blueprint(), config());

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + input)
                .hasCauseInstanceOf(java.nio.charset.MalformedInputException.class);
    }

    @Test
    void missingCsvOpenFailureIdentifiesInputAndPreservesCause() throws Exception {
        Path input = temp.resolve("missing.csv");
        SourceCommandReader reader = new SourceCommandReader(input, blueprint(), config());

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(ItemStreamException.class)
                .hasMessage("Cannot open source CSV: " + input)
                .hasCauseInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void readableCsvStillProducesCommands() throws Exception {
        Path input = temp.resolve("source.csv");
        Files.writeString(input, "old_codes,replacement\n001,002\n");
        SourceCommandReader reader = new SourceCommandReader(input, blueprint(), config());
        reader.open(new ExecutionContext());
        try {
            var commands = reader.read();
            assertThat(commands).hasSize(1);
            assertThat(commands.getFirst().oldIdValues()).containsExactly("001");
            assertThat(commands.getFirst().newIdValue()).isEqualTo("002");
            assertThat(reader.read()).isNull();
        } finally {
            reader.close();
        }
    }
}
