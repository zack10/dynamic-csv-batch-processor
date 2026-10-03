package com.example.csvbatch.batch;

import com.example.csvbatch.execution.Artifacts;
import com.example.csvbatch.persistence.repository.ReplacementRunRepository;
import com.example.csvbatch.persistence.repository.StagedCommandRepository;
import com.example.csvbatch.persistence.repository.StagedPairRepository;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RunReporter {
    private final StagedCommandRepository commands;
    private final StagedPairRepository pairs;
    private final ReplacementRunRepository runs;
    private final JobRepository repository;

    public RunReporter(StagedCommandRepository commands, StagedPairRepository pairs,
                       ReplacementRunRepository runs, JobRepository repository) {
        this.commands = commands;
        this.pairs = pairs;
        this.runs = runs;
        this.repository = repository;
    }

    public void write(JobExecution execution, Path directory) throws Exception {
        String submission = execution.getJobParameters().getString("submissionId");
        var result = new LinkedHashMap<String, Object>();
        result.put("submissionId", submission);
        result.put("executionId", execution.getId());
        result.put("status", execution.getStatus().name());
        result.put("mode", execution.getJobParameters().getString("mode"));
        result.put("startedAt", String.valueOf(execution.getStartTime()));
        result.put("finishedAt", String.valueOf(execution.getEndTime()));
        var summary = commands.summarizeBySubmissionId(submission);
        result.put("sourceRecordsCommitted", summary.sourceRecordsCommitted());
        result.put("canonicalCommands", summary.canonicalCommands());
        result.put("expandedOldValues", pairs.countById_SubmissionId(submission));
        result.put("noOpPairs", pairs.countNoOpsBySubmissionId(submission));
        result.put("redundantPairs", pairs.countRedundantBySubmissionId(submission));
        var counters = runs.findCountersBySubmissionId(submission)
                .orElseThrow(() -> new IllegalStateException("Missing replacement run: " + submission));
        var database = new LinkedHashMap<String, Object>();
        database.put("sql_statements", counters.sqlStatements());
        database.put("affected_rows", counters.affectedRows());
        database.put("zero_matches", counters.zeroMatches());
        database.put("unknown_counts", counters.unknownCounts());
        result.put("database", database);
        var steps = new LinkedHashMap<String, Object>();
        for (String name : new String[]{"preflight", "normalize", "validateCommands", "publishCanonical", "applyUpdates"}) {
            var step = repository.getLastStepExecution(execution.getJobInstance(), name);
            if (step != null) steps.put(name, Map.of("status", step.getStatus().name(), "read", step.getReadCount(),
                    "written", step.getWriteCount(), "rollbacks", step.getRollbackCount(),
                    "committedSourceRecord", step.getExecutionContext().getLong("source.records", 0L),
                    "committedCanonicalRecord", step.getExecutionContext().getLong("canonical.records", 0L)));
        }
        result.put("steps", steps);
        result.put("errors", execution.getAllFailureExceptions().stream().map(Throwable::toString).toList());
        result.put("notes", "Counts describe committed work. Zero matches may include duplicate mappings. Affected rows are update events, not distinct records.");
        Artifacts.writeJson(directory.resolve("report.json"), result);
    }
}
