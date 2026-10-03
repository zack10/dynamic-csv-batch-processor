package com.example.csvbatch.persistence.repository;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Submission identity storage and the lock spanning synchronous job execution. */
@Repository
public class SubmissionRepository {
    @FunctionalInterface
    public interface LockedWork<T> {
        T run() throws Exception;
    }

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public SubmissionRepository(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    public Optional<String> findFingerprint(String submission) {
        return jdbc.queryForList("SELECT fingerprint FROM replacement_run WHERE submission_id = ?",
                String.class, submission).stream().findFirst();
    }

    public void createRun(String submission, String fingerprint, String mode) {
        jdbc.update("INSERT INTO replacement_run(submission_id, fingerprint, mode) VALUES (?, ?, ?)",
                submission, fingerprint, mode);
    }

    public <T> T withExclusiveLock(LockedWork<T> work) throws Exception {
        // This dedicated connection must remain separate from chunk transactions.
        try (var lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try {
                try (var statement = lock.prepareStatement("SELECT id FROM replacement_lock WHERE id = 1 FOR UPDATE NOWAIT");
                     var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("Missing job lock row");
                }
                return work.run();
            } finally {
                lock.rollback();
            }
        }
    }
}
