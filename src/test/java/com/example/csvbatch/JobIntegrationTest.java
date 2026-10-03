package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.execution.Artifacts;
import com.example.csvbatch.persistence.projection.ReplacementRunCounters;
import com.example.csvbatch.persistence.repository.ReplacementRunRepository;
import com.example.csvbatch.persistence.repository.StagedCommandRepository;
import com.example.csvbatch.persistence.repository.StagedPairRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1", "app.work-directory=target/integration-work",
        "spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1"})
class JobIntegrationTest {
    @Autowired
    com.example.csvbatch.persistence.repository.SubmissionRepository submissionRepository;

    @Test
    void submissionLockExcludesOtherConnectionsAndReleasesAfterFailure() throws Exception {
        assertThatThrownBy(() -> submissionRepository.withExclusiveLock(() -> {
            try (var connection = jdbc.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try {
                    assertThatThrownBy(() -> {
                        try (var statement = connection.prepareStatement(
                                "SELECT id FROM replacement_lock WHERE id = 1 FOR UPDATE NOWAIT")) {
                            statement.executeQuery().close();
                        }
                    }).isInstanceOf(java.sql.SQLException.class);
                } finally {
                    connection.rollback();
                }
            }
            throw new IllegalStateException("Simulated submission failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated submission failure");
        assertThat(submissionRepository.withExclusiveLock(() -> "released")).isEqualTo("released");
        assertThat(submissionRepository.withExclusiveLock(() -> "released again")).isEqualTo("released again");
    }

    @Autowired
    SubmissionService submissions;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StagedCommandRepository commands;
    @Autowired
    StagedPairRepository pairs;
    @Autowired
    ReplacementRunRepository runs;
    @Autowired
    PlatformTransactionManager defaultTransactionManager;
    @Autowired
    @Qualifier("batchTransactionManager")
    PlatformTransactionManager batchTransactionManager;
    @Autowired
    @Qualifier("transactionManager")
    PlatformTransactionManager jpaTransactionManager;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1'), ('102', 'A2'), ('KEEP', 'KEEP')");
    }

    private String id() {
        return "test-" + UUID.randomUUID();
    }

    private Path directory(String id) {
        return Path.of("target/integration-work", id);
    }

    @Test
    void appliesBothPropertiesAndPublishesCanonicalArtifacts() throws Exception {
        String id = id();
        var execution = submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900' AND department_id='B1'", Long.class)).isEqualTo(2);
        assertThat(Files.readString(directory(id).resolve("canonical.csv"))).isEqualTo(Files.readString(Path.of("examples/expected-team-a.csv")));
        Artifacts.verify(directory(id), id);
        assertThat(jdbc.queryForObject("SELECT affected_rows FROM replacement_run WHERE submission_id=?", Long.class, id)).isEqualTo(4);
        assertThat(Files.readString(directory(id).resolve("report.json"))).contains("COMPLETED", "sql_statements");
        assertThatThrownBy(() -> submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false))
                .hasMessageContaining("complete");
    }

    @Test
    void validateOnlyNeverUpdatesBusinessRowsAndCannotChangeModeOnRestart() throws Exception {
        String id = id();
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "validate-only", false).getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class)).isZero();
        assertThatThrownBy(() -> submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false))
                .hasMessageContaining("different inputs");
    }

    @Test
    void conflictsAndChainsFailBeforeUpdates() throws Exception {
        for (String name : new String[]{"conflict", "chain"}) {
            String id = id();
            var execution = submissions.submit(id, Path.of("examples/" + name + ".csv"), Path.of("examples/split-list.yaml"), "apply", false);
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='101'", Long.class)).isEqualTo(1);
            assertThat(directory(id).resolve("manifest.json")).doesNotExist();
        }
    }

    @Test
    void databaseFailureRestartsAtLastCommittedChunk() throws Exception {
        String id = id();
        jdbc.execute("ALTER TABLE business_record ADD CONSTRAINT reject_b1 CHECK (department_id <> 'B1')");
        try {
            var failed = submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT affected_rows FROM replacement_run WHERE submission_id=?", Long.class, id)).isEqualTo(2);
        } finally {
            jdbc.execute("ALTER TABLE business_record DROP CONSTRAINT reject_b1");
        }
        var resumed = submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);
        assertThat(resumed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT affected_rows FROM replacement_run WHERE submission_id=?", Long.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(2);
        var report = JsonMapper.builder().build().readTree(Files.readString(directory(id).resolve("report.json")));
        assertThat(report.path("database").path("affected_rows").asLong()).isEqualTo(4);
        assertThat(report.path("database").path("sql_statements").asLong()).isEqualTo(4);
        assertThat(report.path("canonicalCommands").asLong()).isEqualTo(2);
    }

    @Test
    void invalidLastRecordPreventsEveryBusinessUpdate() throws Exception {
        Path input = Path.of("target", id() + ".csv");
        Files.writeString(input, "old_codes,replacement\n101,900\n102,\n");
        String id = id();
        var execution = submissions.submit(id, input, Path.of("examples/split-list.yaml"), "apply", false);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='101'", Long.class)).isEqualTo(1);
        Files.writeString(input, "old_codes,replacement\n101,900\n102,901\n");
        assertThatThrownBy(() -> submissions.submit(id, input, Path.of("examples/split-list.yaml"), "apply", false))
                .hasMessageContaining("different inputs");
    }

    @Test
    void normalizationRestartsWithoutDuplicateStagedCommands() throws Exception {
        String id = id();
        jdbc.execute("ALTER TABLE staged_command ADD CONSTRAINT reject_second_source CHECK (source_record <> 2 OR submission_id <> '" + id + "')");
        try {
            assertThat(submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false).getStatus())
                    .isEqualTo(BatchStatus.FAILED);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE staged_command DROP CONSTRAINT reject_second_source");
        }
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false).getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(2);
    }

    @Test
    void noOpsAndDuplicatesAreReportedAndDoNotConflict() throws Exception {
        Path input = Path.of("target", id() + ".csv");
        Files.writeString(input, "old_codes,replacement\n101,101\n101,900\n101,900\n");
        String id = id();
        assertThat(submissions.submit(id, input, Path.of("examples/split-list.yaml"), "apply", false).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT affected_rows FROM replacement_run WHERE submission_id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT zero_matches FROM replacement_run WHERE submission_id=?", Long.class, id)).isEqualTo(1);
        assertThat(Files.readString(directory(id).resolve("report.json"))).contains("\"noOpPairs\":1", "\"redundantPairs\":1");
    }

    @Test
    void tamperedCanonicalFilePreventsRestartedUpdates() throws Exception {
        String id = id();
        jdbc.execute("ALTER TABLE business_record ADD CONSTRAINT reject_b1 CHECK (department_id <> 'B1')");
        try {
            assertThat(submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false).getStatus())
                    .isEqualTo(BatchStatus.FAILED);
        } finally {
            jdbc.execute("ALTER TABLE business_record DROP CONSTRAINT reject_b1");
        }
        Files.writeString(directory(id).resolve("canonical.csv"), "modified\n");
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false).getStatus())
                .isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE department_id='B1'", Long.class)).isZero();
    }

    @Test
    void jpaReportingDoesNotReplaceTheBatchTransactionManager() {
        assertThat(batchTransactionManager).isInstanceOf(JdbcTransactionManager.class);
        assertThat(defaultTransactionManager).isSameAs(batchTransactionManager);
        assertThat(jpaTransactionManager).isInstanceOf(JpaTransactionManager.class);
    }

    @Test
    void headerOnlySubmissionReportsZeroAggregates() throws Exception {
        String id = id();
        Path input = Path.of("target", id + ".csv");
        Files.writeString(input, "old_codes,replacement\n");
        assertThat(submissions.submit(id, input, Path.of("examples/split-list.yaml"), "validate-only", false).getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
        var report = JsonMapper.builder().build().readTree(Files.readString(directory(id).resolve("report.json")));
        for (String field : new String[]{"sourceRecordsCommitted", "canonicalCommands", "expandedOldValues", "noOpPairs", "redundantPairs"}) {
            assertThat(report.path(field).asLong()).as(field).isZero();
        }
        assertThat(runs.findCountersBySubmissionId(id)).contains(new ReplacementRunCounters(0, 0, 0, 0));
        assertThat(runs.findCountersBySubmissionId(id())).isEmpty();
    }

    @Test
    void repositoryAggregatesKeepSubmissionsPropertiesAndDestinationsSeparate() {
        String submission = id();
        String other = id();
        for (String run : new String[]{submission, other}) {
            jdbc.update("INSERT INTO replacement_run(submission_id, fingerprint, mode) VALUES (?, ?, ?)", run, "test", "validate-only");
        }
        String[][] values = {
                {"customerId", "A", "B"}, {"customerId", "A", "B"}, {"customerId", "A", "B"},
                {"departmentId", "A", "B"}, {"departmentId", "A", "B"},
                {"customerId", "A", "C"}, // Different destination is a different group, even on an invalid submission.
                {"customerId", "A", "A"}, {"customerId", "A", "A"} // Repeated no-ops are excluded from redundancy.
        };
        for (int i = 0; i < values.length; i++) {
            jdbc.update("INSERT INTO staged_pair VALUES (?, ?, ?, ?, ?, ?, ?)",
                    submission, i + 1, 0, 0, values[i][0], values[i][1], values[i][2]);
        }
        jdbc.update("INSERT INTO staged_pair VALUES (?, ?, ?, ?, ?, ?, ?)", other, 1, 0, 0, "customerId", "A", "B");
        assertThat(pairs.countById_SubmissionId(submission)).isEqualTo(8);
        assertThat(pairs.countNoOpsBySubmissionId(submission)).isEqualTo(2);
        assertThat(pairs.countRedundantBySubmissionId(submission)).isEqualTo(3);
        assertThat(pairs.countRedundantBySubmissionId(other)).isZero();

        for (int mapping = 0; mapping < 2; mapping++) {
            jdbc.update("""
                    INSERT INTO staged_command(submission_id, source_record, mapping_index, mapping_name, property_key, old_values, new_value)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, submission, 1, mapping, "mapping-" + mapping, "customerId", "[\"A\"]", "B");
        }
        var summary = commands.summarizeBySubmissionId(submission);
        assertThat(summary.sourceRecordsCommitted()).isEqualTo(1);
        assertThat(summary.canonicalCommands()).isEqualTo(2);
        assertThat(commands.summarizeBySubmissionId(other).canonicalCommands()).isZero();
    }
}
