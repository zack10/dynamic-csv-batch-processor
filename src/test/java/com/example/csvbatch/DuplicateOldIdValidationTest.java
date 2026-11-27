package com.example.csvbatch;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.blueprint.MappingPlan;
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

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1", "app.work-directory=target/integration-work",
        "spring.datasource.url=jdbc:h2:mem:duplicate-validation;DB_CLOSE_DELAY=-1"})
class DuplicateOldIdValidationTest {
    @Autowired SubmissionService submissions;
    @Autowired JdbcTemplate jdbc;
    @Autowired BlueprintLoader blueprints;
    @Autowired AppProperties properties;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1')");
    }

    @Test
    void validTeamSourceStillAppliesCommandsAndMetadata() throws Exception {
        String id = "team-" + UUID.randomUUID();
        var execution = submissions.submit(id, Path.of("examples/team-a.csv"), Path.of("examples/team-a.yaml"), "apply", false);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE department_id='B1'", Long.class)).isEqualTo(1);

        var plan = new MappingPlan(blueprints.load(Path.of("examples/team-a.yaml")),
                new String[]{"previous_primary", "previous_secondary", "replacement", "attribute"}, properties);
        var commands = plan.apply(new String[]{"101", "102", "900", "CUSTOMER"}, 1);
        assertThat(commands.getFirst().newIdValue()).isEqualTo("900");
        assertThat(commands.getFirst().metadataToUpdate()).isEqualTo("customerId");
    }

    @Test
    void duplicateOldIdsFailBeforeStagingAndUpdates() throws Exception {
        String id = "duplicate-" + UUID.randomUUID();
        Path input = Path.of("target", id + ".csv");
        Files.writeString(input, "old_codes,replacement\n101|101,900\n");

        var execution = submissions.submit(id, input, Path.of("examples/split-list.yaml"), "apply", false);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        String report = Files.readString(Path.of("target/integration-work", id, "report.json"));
        assertThat(report).contains("Source record 1", "mapping replace-customer", "Duplicate old ID: 101");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_pair WHERE submission_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='101'", Long.class)).isEqualTo(1);
    }
}
