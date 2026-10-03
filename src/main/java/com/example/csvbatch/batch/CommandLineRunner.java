package com.example.csvbatch.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.launch", havingValue = "true", matchIfMissing = true)
public class CommandLineRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineRunner.class);
    private final SubmissionService submissions;
    private int exitCode;

    public CommandLineRunner(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!args.getNonOptionArgs().isEmpty()) {
                throw new IllegalArgumentException("Use --input, --blueprint, --submissionId and --mode options");
            }

            for (String option : args.getOptionNames()) {
                if (!Set.of("input", "blueprint", "submissionId", "mode", "recover").contains(option)
                        && !option.startsWith("spring.") && !option.startsWith("app.") && !option.startsWith("logging.")) {
                    throw new IllegalArgumentException("Unknown option: " + option);
                }
            }

            String recover = option(args, "recover", "false");
            if (!Set.of("true", "false").contains(recover)) {
                throw new IllegalArgumentException("recover must be true or false");
            }

            var execution = submissions.submit(option(args, "submissionId", null), Path.of(option(args, "input", null)),
                    Path.of(option(args, "blueprint", null)), option(args, "mode", "validate-only"), Boolean.parseBoolean(recover));
            exitCode = execution.getStatus() == BatchStatus.COMPLETED ? 0 : 1;
            LOG.info("Submission finished: status={}, execution={}", execution.getStatus(), execution.getId());
        } catch (Exception ex) {
            exitCode = 1;
            LOG.error("Submission failed: {}", ex.getMessage(), ex);
        }
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        var values = args.getOptionValues(name);
        if (values == null) {
            if (fallback != null) return fallback;
            throw new IllegalArgumentException("Missing --" + name + "=...");
        }
        if (values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Expected one nonempty --" + name);
        }
        return values.getFirst();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
