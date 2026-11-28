package com.example.csvbatch;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.batch.SubmissionService;
import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.blueprint.Blueprint;
import com.example.csvbatch.blueprint.MappingPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void whitespaceOnlyDifferencesRemainDistinctWithoutTrim() throws Exception {
        jdbc.execute("INSERT INTO business_record(customer_id, department_id) VALUES (' 101', 'A1')");
        submitWhitespaceCase(List.of(), "distinct");
        var plan = whitespacePlan(List.of());
        var commands = plan.apply(new String[]{"101", " 101", "900"}, 1);
        assertThat(commands.getFirst().oldIdValues()).containsExactly("101", " 101");
    }

    @Test
    void distinctRemovesWhitespaceDuplicatesAfterTrim() throws Exception {
        var execution = submitWhitespaceCase(List.of("trim"), "trimmed-distinct");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void trimMakesWhitespaceDuplicatesFailWithoutDistinct() throws Exception {
        var execution = submitWhitespaceCase(List.of("trim"), "trimmed");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        var plan = whitespacePlan(List.of("trim"));
        assertThatThrownBy(() -> plan.apply(new String[]{"101", " 101", "900"}, 1))
                .hasMessageContaining("Source record 1")
                .hasMessageContaining("mapping whitespace")
                .hasMessageContaining("Duplicate old ID: 101");
    }

    private JobExecution submitWhitespaceCase(List<String> transforms, String suffix) throws Exception {
        String id = "whitespace-" + suffix + "-" + UUID.randomUUID();
        Path dir = Path.of("target", id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("input.csv"), "old,old2,replacement\n101, 101,900\n");
        String yaml = whitespaceYaml(transforms);
        if (suffix.equals("trimmed")) yaml = yaml.replace("distinct: true", "");
        Files.writeString(dir.resolve("blueprint.yaml"), yaml);
        return submissions.submit(id, dir.resolve("input.csv"), dir.resolve("blueprint.yaml"), "apply", false);
    }

    private String whitespaceYaml(List<String> transforms) {
        String transformLine = transforms.isEmpty() ? "[]" : "[trim]";
        return """
                version: 1
                name: whitespace
                input:
                  encoding: UTF-8
                  delimiter: ","
                  quote: '\"'
                  escapeMode: doubled-quote
                  header: true
                  headerMatching: exact
                  ignoreEmptyRecords: true
                mappings:
                  - name: whitespace
                    oldIdValues:
                      source:
                        columns: [old, old2]
                      transforms: %s
                      distinct: true
                    newIdValue:
                      source:
                        column: replacement
                    metadataToUpdate:
                      source:
                        column: replacement
                      valueMap:
                        900: customerId
                      onUnmapped: error
                """.formatted(transformLine);
    }

    private MappingPlan whitespacePlan(List<String> transforms) {
        var value = new Blueprint.Value(new Blueprint.Source(null, null, null,
                List.of("old", "old2"), null), null, transforms, false, false, null, null);
        var replacement = new Blueprint.Value(new Blueprint.Source(null, 2, null, null, null),
                null, List.of(), false, false, null, null);
        var metadata = new Blueprint.Value(new Blueprint.Source(null, null, "customerId", null, null),
                null, List.of(), false, false, null, null);
        var blueprint = new Blueprint(1, "whitespace", null,
                List.of(new Blueprint.Mapping("whitespace", value, replacement, metadata)));
        return new MappingPlan(blueprint, new String[]{"old", "old2", "replacement"}, properties);
    }

    @Test
    void duplicateOldIdsFailBeforeStagingAndUpdates() throws Exception {
        String id = "duplicate-" + UUID.randomUUID();
        Path dir = Path.of("target", id);
        Files.createDirectories(dir);
        Path input = dir.resolve("input.csv");
        Path blueprint = dir.resolve("blueprint.yaml");
        Files.writeString(input, "old,old2,replacement\n101,101,900\n");
        Files.writeString(blueprint, whitespaceYaml(List.of()).replace("distinct: true", ""));

        var execution = submissions.submit(id, input, blueprint, "apply", false);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        String report = Files.readString(Path.of("target/integration-work", id, "report.json"));
        assertThat(report).contains("Source record 1", "mapping whitespace", "Duplicate old ID: 101");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_command WHERE submission_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staged_pair WHERE submission_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record WHERE customer_id='101'", Long.class)).isEqualTo(1);
    }
}
