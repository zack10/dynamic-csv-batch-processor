package com.example.csvbatch.batch;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.execution.Artifacts;
import com.example.csvbatch.execution.TargetValidator;
import com.example.csvbatch.normalization.CanonicalCodec;
import com.example.csvbatch.persistence.repository.SubmissionRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Service
public class SubmissionService {
    private final AppProperties config;
    private final SubmissionRepository submissions;
    private final BlueprintLoader blueprints;
    private final JobOperator operator;
    private final Job replacementJob;
    private final JobRepository repository;
    private final RunReporter reporter;
    private final TargetValidator targets;

    public SubmissionService(AppProperties config, SubmissionRepository submissions, BlueprintLoader blueprints,
                             JobOperator operator, Job replacementJob, JobRepository repository, RunReporter reporter, TargetValidator targets) {
        this.config = config;
        this.submissions = submissions;
        this.blueprints = blueprints;
        this.operator = operator;
        this.replacementJob = replacementJob;
        this.repository = repository;
        this.reporter = reporter;
        this.targets = targets;
    }

    public JobExecution submit(String submission, Path input, Path blueprint, String mode, boolean recover) throws Exception {
        if (!submission.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")) {
            throw new IllegalArgumentException("Invalid submissionId");
        }
        if (!mode.equals("apply") && !mode.equals("validate-only")) {
            throw new IllegalArgumentException("mode must be apply or validate-only");
        }
        blueprints.load(blueprint);
        return submissions.withExclusiveLock(() -> {
            targets.validate(); // Also checked on restart, when preflight may already be complete.
            Path directory = config.workDirectory().toAbsolutePath().normalize().resolve(submission);
            Files.createDirectories(directory);
            String inputHash = Artifacts.sha256(input);
            String yamlHash = Artifacts.sha256(blueprint);
            String description = CanonicalCodec.json(new TreeMap<>(Map.of("engine", "1", "input", inputHash, "blueprint", yamlHash,
                    "mode", mode, "table", config.targetTable(), "properties", new TreeMap<>(config.properties()),
                    "directory", directory.toString())));
            String fingerprint = HexFormat.of()
                    .formatHex(MessageDigest
                            .getInstance("SHA-256")
                            .digest(description.getBytes(StandardCharsets.UTF_8))
                    );
            var existing = submissions.findFingerprint(submission);
            if (existing.isPresent() && !existing.get().equals(fingerprint)) {
                throw new IllegalArgumentException("Submission is bound to different inputs, mode, registry or work directory; use a new submissionId");
            }
            snapshot(input, directory.resolve("source.csv"), inputHash, !existing.isEmpty());
            snapshot(blueprint, directory.resolve("blueprint.yaml"), yamlHash, !existing.isEmpty());
            if (existing.isEmpty()) {
                submissions.createRun(submission, fingerprint, mode);
            }
            var parameters = new JobParametersBuilder().addString("submissionId", submission, true)
                    .addString("input", directory.resolve("source.csv").toString(), false)
                    .addString("blueprint", directory.resolve("blueprint.yaml").toString(), false)
                    .addString("directory", directory.toString(), false).addString("mode", mode, false).toJobParameters();
            var last = repository.getLastJobExecution("replacementJob", parameters);
            if (last != null && last.isRunning()) {
                if (!recover) {
                    throw new IllegalStateException("Prior execution is still marked running. After confirming its process is dead, repeat with --recover=true");
                }
                operator.recover(last);
            }
            // The default synchronous operator is required: the lock spans actual execution.
            var execution = operator.start(replacementJob, parameters);
            if (execution.isRunning()) {
                throw new IllegalStateException("An asynchronous JobOperator is unsupported");
            }
            reporter.write(execution, directory);
            return execution;
        });
    }

    private static void snapshot(Path source, Path target, String checksum, boolean restart) throws Exception {
        if (Files.exists(target)) {
            if (!Artifacts.sha256(target).equals(checksum)) {
                throw new IllegalArgumentException("Snapshot integrity check failed: " + target);
            }
            return;
        }
        if (restart) {
            throw new IllegalArgumentException("Required restart snapshot is missing: " + target);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        if (!Artifacts.sha256(temporary).equals(checksum)) {
            throw new IllegalArgumentException("Input changed while snapshotting");
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }
}
