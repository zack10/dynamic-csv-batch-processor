package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.persistence.entity.ReplacementPropertyCountEntity;
import com.example.csvbatch.persistence.projection.ReplacementPropertyCount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true, transactionManager = "transactionManager")
public interface ReplacementPropertyCountRepository extends Repository<ReplacementPropertyCountEntity, ReplacementPropertyCountEntity.Key> {
    @Query("select new com.example.csvbatch.persistence.projection.ReplacementPropertyCount(c.propertyKey, c.updatedRows) " +
            "from ReplacementPropertyCount c where c.submissionId = :submissionId order by c.propertyKey")
    List<ReplacementPropertyCount> findBySubmissionId(@Param("submissionId") String submissionId);
}
