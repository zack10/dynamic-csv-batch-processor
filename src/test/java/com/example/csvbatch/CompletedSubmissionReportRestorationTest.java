package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1", "app.work-directory=target/integration-work",
        "spring.datasource.url=jdbc:h2:mem:report-restoration;DB_CLOSE_DELAY=-1"})
class CompletedSubmissionReportRestorationTest {
    @Autowired
    SubmissionService submissions;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2'), ('KEEP', 'KEEP')");
    }

    @Test
    void restoresMissingReportFromCompletedExecutionWithoutRerunningJob() throws Exception {
        String id = "report-" + UUID.randomUUID();
        Path directory = Path.of("target/integration-work", id);
        var first = submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        long affectedRows = jdbc.queryForObject("SELECT affected_rows FROM replacement_run WHERE submission_id=?", Long.class, id);
        long businessRows = jdbc.queryForObject("SELECT COUNT(*) FROM business_record", Long.class);
        Files.delete(directory.resolve("report.json"));

        var restored = submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);

        assertThat(restored.getId()).isEqualTo(first.getId());
        assertThat(restored.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(directory.resolve("report.json")).exists();
        assertThat(jdbc.queryForObject("SELECT affected_rows FROM replacement_run WHERE submission_id=?", Long.class, id))
                .isEqualTo(affectedRows);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record", Long.class)).isEqualTo(businessRows);
        assertThatThrownBy(() -> submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false))
                .hasMessageContaining("already complete");
    }
}
