package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1",
        "app.work-directory=target/invalid-dry-run-work",
        "spring.datasource.url=jdbc:h2:mem:invalid-dry-run;DB_CLOSE_DELAY=-1"})
class InvalidDryRunIntegrationTest {
    @Autowired SubmissionService submissions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void invalidSubmissionFailsTheSameWithAndWithoutDryRun(@TempDir Path temp) throws Exception {
        Path invalidInput = temp.resolve("invalid.csv");
        Files.writeString(invalidInput,
                "previous_primary;previous_secondary;replacement;attribute\n101;102;900\n");
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2')");

        var normal = submissions.submit("normal-" + UUID.randomUUID(), invalidInput,
                Path.of("examples/team-a.yaml"), "apply", false);
        var dry = submissions.submit("dry-" + UUID.randomUUID(), invalidInput,
                Path.of("examples/team-a.yaml"), "apply", false, true);

        assertThat(normal.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(dry.getStatus()).isEqualTo(normal.getStatus());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record", Long.class)).isEqualTo(2);
    }
}
