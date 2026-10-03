package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in: real >1 GiB source through staging, validation, export and canonical verification.
 */
@EnabledIfSystemProperty(named = "largeFileTest", matches = "true")
@SpringBootTest(properties = {"app.launch=false", "app.work-directory=target/large-work",
        "spring.datasource.url=jdbc:h2:file:./target/large-db-${random.uuid};CACHE_SIZE=8192;DB_CLOSE_ON_EXIT=FALSE"})
class LargeFileTest {
    @Autowired
    SubmissionService submissions;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void validatesMoreThanOneGiBWithBoundedHeap() throws Exception {
        String id = "large-" + UUID.randomUUID();
        Path input = Path.of("target", id + ".csv");
        String padding = "x".repeat(8000);
        long bytes = 0, rows = 0;
        try (var out = Files.newBufferedWriter(input)) {
            String header = "old_codes,replacement,unused\n";
            out.write(header);
            bytes += header.length();
            while (bytes <= 1100L * 1024 * 1024) {
                String row = "old_" + rows + ",new_" + rows + "," + padding + "\n";
                out.write(row);
                bytes += row.length();
                rows++;
            }
        }
        assertThat(Files.size(input)).isGreaterThan(1024L * 1024 * 1024);
        var execution = submissions.submit(id, input, Path.of("examples/split-list.yaml"), "validate-only", false);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(rows);
        assertThat(jdbc.queryForObject("SELECT sql_statements FROM replacement_run WHERE submission_id=?", Long.class, id)).isZero();
        System.out.printf("Large-file verification: %,d bytes, %,d rows, max JVM heap %,d bytes%n", bytes, rows, Runtime.getRuntime().maxMemory());
    }
}
