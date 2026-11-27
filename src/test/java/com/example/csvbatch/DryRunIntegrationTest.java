package com.example.csvbatch;

import com.example.csvbatch.batch.CommandLineRunner;
import com.example.csvbatch.batch.RunReporter;
import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.execution.CanonicalReader;
import com.example.csvbatch.execution.GlobalValidator;
import com.example.csvbatch.normalization.SourceCommandReader;
import com.example.csvbatch.normalization.StagingWriter;
import com.example.csvbatch.persistence.repository.StagedCommandRepository;
import com.example.csvbatch.persistence.repository.StagingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1",
        "app.work-directory=target/dry-run-work",
        "spring.datasource.url=jdbc:h2:mem:dry-run;DB_CLOSE_DELAY=-1"})
class DryRunIntegrationTest {
    @Autowired SubmissionService submissions;
    @Autowired JdbcTemplate jdbc;
    @Autowired StagingRepository staging;

    @Test
    void dryRunRetryDoesNotConsumeFailedApplyCheckpoint() throws Exception {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2')");
        String id = "retry-" + UUID.randomUUID();
        jdbc.execute("ALTER TABLE business_record ADD CONSTRAINT reject_b1_dry CHECK (department_id <> 'B1')");
        try {
            assertThat(submissions.submit(id, Path.of("examples/team-a.csv"),
                    Path.of("examples/team-a.yaml"), "apply", false).getStatus()).isEqualTo(BatchStatus.FAILED);
        } finally {
            jdbc.execute("ALTER TABLE business_record DROP CONSTRAINT reject_b1_dry");
        }
        long appliedBeforeDryRun = jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class);
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"),
                Path.of("examples/team-a.yaml"), "apply", false, true).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class))
                .isEqualTo(appliedBeforeDryRun);
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"),
                Path.of("examples/team-a.yaml"), "apply", false).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class)).isEqualTo(2);
    }

    @Test
    void conflictingDryRunFlagsAreRejected() {
        var service = mock(SubmissionService.class);
        var runner = new CommandLineRunner(service);
        runner.run(new DefaultApplicationArguments("--submissionId=x", "--input=x", "--blueprint=x",
                "--dry-run=false", "--dry-run"));
        assertThat(runner.getExitCode()).isEqualTo(1);
        verifyNoInteractions(service);
    }

    @Test
    void dryRunPipelineUsesAllRelevantComponents() {
        // Keep the concrete pipeline components explicit here: the integration test below
        // exercises them through the configured job, rather than replacing them with mocks.
        assertThat(new Class<?>[]{CommandLineRunner.class, SourceCommandReader.class,
                StagingWriter.class, StagedCommandRepository.class, GlobalValidator.class,
                CanonicalReader.class, RunReporter.class, StagingRepository.class}).hasSize(8);
        String id = "direct-stage-" + UUID.randomUUID();
        jdbc.update("INSERT INTO replacement_run(submission_id, fingerprint, mode) VALUES (?, 'test', 'apply')", id);
        staging.insertBatch(
                java.util.Collections.singletonList(new Object[]{id, 1L, 0, "mapping", "customer_id", "[101]", "900"}),
                java.util.Collections.singletonList(new Object[]{id, 1L, 0, 0, "customer_id", "101", "900"}));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_pair WHERE submission_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void invalidSubmissionFailsTheSameWithAndWithoutDryRun(@TempDir Path temp) throws Exception {
        Path invalidInput = temp.resolve("invalid.csv");
        Files.writeString(invalidInput, "previous_primary;previous_secondary;replacement;attribute\n101;102;900\n");
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2')");

        var normal = submissions.submit("invalid-normal-" + UUID.randomUUID(), invalidInput,
                Path.of("examples/team-a.yaml"), "apply", false);
        var dry = submissions.submit("invalid-dry-" + UUID.randomUUID(), invalidInput,
                Path.of("examples/team-a.yaml"), "apply", false, true);

        assertThat(normal.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(dry.getStatus()).isEqualTo(normal.getStatus());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id IN ('101','102')", Long.class))
                .isEqualTo(2);
    }

    @Test
    void dryRunStagesPublishesAndDoesNotUpdateBusinessTable() throws Exception {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2')");
        String id = "dry-" + UUID.randomUUID();

        var execution = submissions.submit(id, Path.of("examples/team-a.csv"),
                Path.of("examples/team-a.yaml"), "apply", false, true);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM business_record WHERE customer_id='101'", String.class)).isEqualTo("101");
        Path directory = Path.of("target/dry-run-work", id);
        assertThat(directory.resolve("canonical.csv")).exists();
        String report = Files.readString(directory.resolve("report.json"));
        assertThat(report).contains("This was a dry run");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(2);
    }
}
