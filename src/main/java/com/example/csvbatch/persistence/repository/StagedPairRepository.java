package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.persistence.entity.StagedPairEntity;
import com.example.csvbatch.persistence.entity.StagedPairId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true, transactionManager = "transactionManager")
public interface StagedPairRepository extends Repository<StagedPairEntity, StagedPairId> {
    long countById_SubmissionId(String submissionId);

    @Query("""
            select count(pair) from StagedPair pair
            where pair.id.submissionId = :submissionId and pair.oldValue = pair.newValue
            """)
    long countNoOpsBySubmissionId(@Param("submissionId") String submissionId);

    /** Hibernate HQL derived root keeps grouping and summation entirely in the database. */
    @Query("""
            select coalesce(sum(duplicates.occurrences - 1L), 0L)
            from (
                select count(pair) as occurrences
                from StagedPair pair
                where pair.id.submissionId = :submissionId and pair.oldValue <> pair.newValue
                group by pair.propertyKey, pair.oldValue, pair.newValue
                having count(pair) > 1L
            ) duplicates
            """)
    long countRedundantBySubmissionId(@Param("submissionId") String submissionId);
}
