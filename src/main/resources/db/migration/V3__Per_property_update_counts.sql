CREATE TABLE replacement_property_count (
    submission_id VARCHAR(80) NOT NULL,
    property_key VARCHAR(63) NOT NULL,
    updated_rows BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (submission_id, property_key),
    FOREIGN KEY (submission_id) REFERENCES replacement_run(submission_id)
);
