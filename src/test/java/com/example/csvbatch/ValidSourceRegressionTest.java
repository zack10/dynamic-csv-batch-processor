package com.example.csvbatch;

import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.blueprint.MappingPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.launch=false", "app.chunk-size=1", "app.work-directory=target/integration-work",
        "spring.datasource.url=jdbc:h2:mem:valid-source-regression;DB_CLOSE_DELAY=-1"})
class ValidSourceRegressionTest {
    @Autowired SubmissionService submissions;
    @Autowired BlueprintLoader blueprints;
    @Autowired AppProperties properties;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM business_record");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('101', 'A1')");
        jdbc.update("INSERT INTO business_record(customer_id, department_id) VALUES ('102', 'A2')");
    }

    @Test
    void teamSourceIsStillStagedAndApplied() throws Exception {
        String id = "valid-team-" + UUID.randomUUID();
        assertThat(submissions.submit(id, Path.of("examples/team-a.csv"),
                Path.of("examples/team-a.yaml"), "apply", false).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='900'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE department_id='B1'", Long.class)).isEqualTo(2);
    }

    @Test
    void mappingPlanKeepsNewIdAndMetadataCompilation() throws Exception {
        var plan = new MappingPlan(blueprints.load(Path.of("examples/team-a.yaml")),
                new String[]{"previous_primary", "previous_secondary", "replacement", "attribute"}, properties);
        var commands = plan.apply(new String[]{"101", "102", "900", "CUSTOMER"}, 1);
        assertThat(commands).hasSize(1);
        assertThat(commands.getFirst().newIdValue()).isEqualTo("900");
        assertThat(commands.getFirst().metadataToUpdate()).isEqualTo("customerId");
    }
}
