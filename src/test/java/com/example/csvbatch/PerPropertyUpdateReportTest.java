package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.persistence.repository.ReplacementUpdateRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1", "app.work-directory=target/integration-work",
        "spring.datasource.url=jdbc:h2:mem:per-property;DB_CLOSE_DELAY=-1"})
class PerPropertyUpdateReportTest {
    @Autowired SubmissionService submissions;
    @Autowired ReplacementUpdateRepository updates;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2'), ('KEEP', 'KEEP')");
    }

    @Test
    void reportsUpdatedRowsByLogicalProperty() throws Exception {
        String id = "property-" + UUID.randomUUID();
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false)
                .getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var report = JsonMapper.builder().build().readTree(Files.readString(Path.of("target/integration-work", id, "report.json")));
        assertThat(report.path("database").path("updated_rows_by_property").toString())
                .isEqualTo("{\"customerId\":2,\"departmentId\":2}");
    }

    @Test
    void failedChunkRollsBackPropertyTotalsBeforeRetry() throws Exception {
        String id = "property-retry-" + UUID.randomUUID();
        submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);
        long before = jdbc.queryForObject("SELECT updated_rows FROM replacement_property_count "
                + "WHERE submission_id = ? AND property_key = 'customerId'", Long.class, id);
        jdbc.update("UPDATE business_record SET customer_id = '101' WHERE department_id = 'B1'");
        try {
            transactions.executeWithoutResult(status -> {
                updates.applyAndRecord(id, java.util.Map.of("customerId",
                        java.util.List.of(new Object[]{"900", "101"}, new Object[]{"901", "102"})));
                throw new IllegalStateException("injected chunk failure");
            });
        } catch (IllegalStateException expected) {
            // The failed chunk, including its property count, must be rolled back.
        }
        assertThat(jdbc.queryForObject("SELECT updated_rows FROM replacement_property_count "
                + "WHERE submission_id = ? AND property_key = 'customerId'", Long.class, id)).isEqualTo(before);
        jdbc.update("UPDATE business_record SET customer_id = '101' WHERE department_id = 'B1'");
        updates.applyAndRecord(id, java.util.Map.of("customerId",
                java.util.List.of(new Object[]{"900", "101"}, new Object[]{"901", "102"})));
        assertThat(jdbc.queryForObject("SELECT updated_rows FROM replacement_property_count "
                + "WHERE submission_id = ? AND property_key = 'customerId'", Long.class, id)).isEqualTo(before + 2);
    }

    @Test
    void reportsZeroMatchesAndRestoresPersistedCounts() throws Exception {
        String id = "property-zero-" + UUID.randomUUID();
        Path dir = Files.createTempDirectory("property-zero");
        Path input = dir.resolve("input.csv");
        Files.writeString(input, "previous_primary;previous_secondary;replacement;attribute\n"
                + "absent;also-absent;900;CUSTOMER\nA1;A2;B1;DEPARTMENT\n");
        Path reportPath = Path.of("target/integration-work", id, "report.json");
        Path blueprint = Path.of("examples/team-a.yaml");
        submissions.submit(id, input, blueprint, "apply", false);
        var first = JsonMapper.builder().build().readTree(Files.readString(reportPath));
        assertThat(first.path("database").path("updated_rows_by_property").toString())
                .isEqualTo("{\"customerId\":0,\"departmentId\":2}");
        assertThat(jdbc.queryForList("SELECT property_key, updated_rows FROM replacement_property_count "
                + "WHERE submission_id = ? ORDER BY property_key", id)).hasSize(2);
        Files.delete(reportPath);
        submissions.submit(id, input, blueprint, "apply", false);
        var restored = JsonMapper.builder().build().readTree(Files.readString(reportPath));
        assertThat(restored.path("database").path("updated_rows_by_property").toString())
                .isEqualTo(first.path("database").path("updated_rows_by_property").toString());
    }
}
