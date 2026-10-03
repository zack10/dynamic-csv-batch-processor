package com.example.csvbatch.batch;

import com.example.csvbatch.AppProperties;
import com.example.csvbatch.blueprint.BlueprintLoader;
import com.example.csvbatch.execution.*;
import com.example.csvbatch.normalization.Command;
import com.example.csvbatch.normalization.SourceCommandReader;
import com.example.csvbatch.normalization.StagingWriter;
import com.example.csvbatch.persistence.repository.ReplacementUpdateRepository;
import com.example.csvbatch.persistence.repository.StagingRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Configuration
public class JobConfiguration {
    @Bean
    Job replacementJob(JobRepository repository, Step preflight, Step normalize, Step validateCommands,
                       Step publishCanonical, Step applyUpdates) {
        return new JobBuilder("replacementJob", repository)
                .start(preflight)
                .next(normalize)
                .next(validateCommands)
                .next(publishCanonical)
                .next(applyUpdates)
                .build();
    }

    @Bean
    Step preflight(JobRepository repository, PlatformTransactionManager tx, TargetValidator targets) {
        return new StepBuilder("preflight", repository)
                .tasklet((contribution, context) -> {
                    targets.validate();
                    return RepeatStatus.FINISHED;
                }, tx).build();
    }

    @Bean
    @StepScope
    ItemStreamReader<List<Command>> sourceReader(BlueprintLoader loader, AppProperties limits,
                                                 @Value("#{jobParameters['input']}") String input,
                                                 @Value("#{jobParameters['blueprint']}") String blueprint) throws Exception {
        return new SourceCommandReader(Path.of(input), loader.load(Path.of(blueprint)), limits);
    }

    @Bean
    @StepScope
    ItemWriter<List<Command>> stagingWriter(StagingRepository staging, AppProperties limits,
                                            @Value("#{jobParameters['submissionId']}") String submission) {
        return new StagingWriter(staging, submission, limits);
    }

    @Bean
    Step normalize(JobRepository repository, PlatformTransactionManager tx, AppProperties limits,
                   ItemStreamReader<List<Command>> sourceReader, ItemWriter<List<Command>> stagingWriter) {
        return new StepBuilder("normalize", repository).<List<Command>, List<Command>>chunk(limits.chunkSize())
                .transactionManager(tx)
                .reader(sourceReader)
                .writer(stagingWriter)
                .build();
    }

    @Bean
    Step validateCommands(JobRepository repository, PlatformTransactionManager tx, GlobalValidator validator) {
        return new StepBuilder("validateCommands", repository)
                .tasklet((contribution, context) -> {
                    validator.validate(contribution.getStepExecution().getJobParameters().getString("submissionId"));
                    return RepeatStatus.FINISHED;
                }, tx).build();
    }

    @Bean
    Step publishCanonical(JobRepository repository, PlatformTransactionManager tx, Artifacts artifacts) {
        return new StepBuilder("publishCanonical", repository)
                .tasklet((contribution, context) -> {
                    var parameters = contribution.getStepExecution().getJobParameters();
                    artifacts.publish(parameters.getString("submissionId"), Path.of(Objects.requireNonNull(parameters.getString("directory"))));
                    return RepeatStatus.FINISHED;
                }, tx).build();
    }

    @Bean
    @StepScope
    ItemStreamReader<Command> canonicalReader(AppProperties limits,
                                              @Value("#{jobParameters['directory']}") String directory,
                                              @Value("#{jobParameters['submissionId']}") String submission,
                                              @Value("#{jobParameters['mode']}") String mode) {
        return new CanonicalReader(Path.of(directory), submission, mode.equals("apply"), limits);
    }

    @Bean
    @StepScope
    ItemWriter<Command> replacementWriter(ReplacementUpdateRepository replacements, AppProperties limits,
                                          @Value("#{jobParameters['submissionId']}") String submission) {
        return new ReplacementWriter(replacements, limits, submission);
    }

    @Bean
    Step applyUpdates(JobRepository repository, PlatformTransactionManager tx, AppProperties limits,
                      ItemStreamReader<Command> canonicalReader, ItemWriter<Command> replacementWriter) {
        return new StepBuilder("applyUpdates", repository).<Command, Command>chunk(limits.chunkSize())
                .transactionManager(tx).reader(canonicalReader).writer(replacementWriter)
                .faultTolerant().retryPolicy(RetryPolicy.builder()
                        .includes(TransientDataAccessException.class)
                        .maxRetries(limits.maxDatabaseAttempts() - 1L)
                        .delay(Duration.ofMillis(200))
                        .multiplier(2)
                        .maxDelay(Duration.ofSeconds(2))
                        .build())
                .build();
    }
}
