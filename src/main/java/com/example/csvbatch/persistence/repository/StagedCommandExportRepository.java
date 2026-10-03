package com.example.csvbatch.persistence.repository;

import com.example.csvbatch.normalization.CanonicalCodec;
import com.example.csvbatch.normalization.Command;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bounded JDBC reads of command payloads for artifact export. */
@Repository
public class StagedCommandExportRepository {
    public record ExportRow(long id, Command command) {
    }

    private final JdbcTemplate jdbc;

    public StagedCommandExportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExportRow> findExportBatch(String submissionId, long afterCommandId) {
        // Keyset pagination avoids both an unbounded result set and OFFSET scans.
        return jdbc.query("""
                        SELECT command_id, source_record, mapping_index, mapping_name, old_values, new_value, property_key
                        FROM staged_command WHERE submission_id = ?
                        AND command_id > ? ORDER BY command_id FETCH FIRST 500 ROWS ONLY
                        """, (rs, n) -> new ExportRow(rs.getLong(1),
                        new Command(rs.getLong(2), rs.getInt(3), rs.getString(4),
                                CanonicalCodec.oldValues(rs.getString(5)), rs.getString(6), rs.getString(7))),
                submissionId, afterCommandId);
    }
}
