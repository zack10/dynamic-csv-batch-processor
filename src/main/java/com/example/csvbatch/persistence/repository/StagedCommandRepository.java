package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.persistence.entity.StagedCommandEntity;
import com.example.csvbatch.persistence.projection.StagedCommandSummary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Narrow Spring Data JPA repository exposing aggregate reads only. */
@Transactional(readOnly = true, transactionManager = "transactionManager")
public interface StagedCommandRepository extends Repository<StagedCommandEntity, Long> {
    @Query("""
            select new com.example.csvbatch.persistence.projection.StagedCommandSummary(
                coalesce(max(command.sourceRecord), 0L), count(command))
            from StagedCommand command
            where command.submissionId = :submissionId
            """)
    StagedCommandSummary summarizeBySubmissionId(@Param("submissionId") String submissionId);
}
