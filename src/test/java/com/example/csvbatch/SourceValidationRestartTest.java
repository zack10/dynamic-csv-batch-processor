package com.example.csvbatch.batch;

import com.example.csvbatch.normalization.SourceValidationTasklet;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

import static org.mockito.Mockito.*;

class SourceValidationRestartTest {
    @Test
    void skipsSourceRereadWhenNormalizeAlreadyCompleted() throws Exception {
        SourceValidationTasklet validator = mock(SourceValidationTasklet.class);
        JobConfiguration configuration = new JobConfiguration();
        Step step = configuration.sourceValidation(mock(JobRepository.class), mock(PlatformTransactionManager.class), validator);
        Tasklet tasklet = ((TaskletStep) step).getTasklet();

        JobParameters parameters = new JobParametersBuilder()
                .addString("input", "/does-not-need-to-exist.csv")
                .addString("blueprint", "/does-not-need-to-exist.yaml")
                .toJobParameters();
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "replacementJob"), parameters);
        StepExecution normalize = new StepExecution("normalize", job);
        normalize.setStatus(BatchStatus.COMPLETED);
        job.addStepExecution(normalize);
        StepExecution source = new StepExecution("sourceValidation", job);

        tasklet.execute(new StepContribution(source), new ChunkContext(new org.springframework.batch.core.scope.context.StepContext(source)));

        verifyNoInteractions(validator);
    }
}
