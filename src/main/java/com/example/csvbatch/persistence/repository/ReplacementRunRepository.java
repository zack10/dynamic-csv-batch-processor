package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.persistence.entity.ReplacementRunEntity;
import com.example.csvbatch.persistence.projection.ReplacementRunCounters;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true, transactionManager = "transactionManager")
public interface ReplacementRunRepository extends Repository<ReplacementRunEntity, String> {
    @Query("""
            select new com.example.csvbatch.persistence.projection.ReplacementRunCounters(
                run.sqlStatements, run.affectedRows, run.zeroMatches, run.unknownCounts)
            from ReplacementRun run where run.submissionId = :submissionId
            """)
    Optional<ReplacementRunCounters> findCountersBySubmissionId(@Param("submissionId") String submissionId);
}
