package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.execution.Artifacts;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Audit reproductions: the tampering test documents current vulnerable behavior.
 */
@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1",
    "app.work-directory=target/security-audit-work",
    "spring.datasource.url=jdbc:h2:mem:security-audit;DB_CLOSE_DELAY=-1"})
class SecurityAuditTest {
    @Autowired
    SubmissionService submissions;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rewrittenManifestAllowsUnstagedReplacementOnRestart() throws Exception {
        String id = "audit-" + UUID.randomUUID();
        Path directory = Path.of("target/security-audit-work", id);
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('KEEP', 'KEEP')");
        jdbc.execute("ALTER TABLE business_record ADD CONSTRAINT audit_reject_900 CHECK (customer_id <> '900')");
        try {
            assertThat(submissions.submit(id, Path.of("examples/team-a.csv"),
                Path.of("examples/team-a.yaml"), "apply", false).getStatus()).isEqualTo(BatchStatus.FAILED);
        } finally {
            jdbc.execute("ALTER TABLE business_record DROP CONSTRAINT audit_reject_900");
        }
        // Simulate an attacker who can write artifacts but cannot change the source or database.
        Files.writeString(directory.resolve("canonical.csv"),
            "old_id_values,new_id_value,metadata_to_update\n\"[\"\"KEEP\"\"]\",\"PWNED\",\"customerId\"\n");
        var mapper = JsonMapper.builder().build();
        var manifest = (tools.jackson.databind.node.ObjectNode) mapper.readTree(
            Files.readString(directory.resolve("manifest.json")));
        manifest.put("canonicalSha256", Artifacts.sha256(directory.resolve("canonical.csv")));
        manifest.put("commands", 1);
        Files.writeString(directory.resolve("manifest.json"), mapper.writeValueAsString(manifest));
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"),
            Path.of("examples/team-a.yaml"), "apply", false).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='PWNED'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_pair WHERE submission_id=? AND old_value='KEEP'", Long.class, id)).isZero();
    }

    @Test
    void traversalSubmissionIsRejectedBeforeCreatingArtifacts() {
        assertThatThrownBy(() -> submissions.submit("../escape", Path.of("examples/team-a.csv"),
            Path.of("examples/team-a.yaml"), "validate-only", false))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid submissionId");
    }
}
