CREATE TABLE data_source_config (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(32) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    database_name VARCHAR(128) NOT NULL,
    username VARCHAR(128) NOT NULL,
    encrypted_password VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_test_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_data_source_name UNIQUE (name)
);
CREATE TABLE sync_job (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    source_data_source_id VARCHAR(36) NOT NULL,
    source_table VARCHAR(128) NOT NULL,
    target_data_source_id VARCHAR(36) NOT NULL,
    target_table VARCHAR(128) NOT NULL,
    write_mode VARCHAR(32) NOT NULL,
    parallelism INTEGER NOT NULL,
    batch_size INTEGER NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_sync_job_name UNIQUE (name),
    CONSTRAINT fk_job_source FOREIGN KEY (source_data_source_id) REFERENCES data_source_config(id),
    CONSTRAINT fk_job_target FOREIGN KEY (target_data_source_id) REFERENCES data_source_config(id)
);
CREATE TABLE sync_run (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    seatunnel_job_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    source_read_count BIGINT NOT NULL DEFAULT 0,
    sink_write_count BIGINT NOT NULL DEFAULT 0,
    source_qps DOUBLE PRECISION NOT NULL DEFAULT 0,
    sink_qps DOUBLE PRECISION NOT NULL DEFAULT 0,
    source_bytes BIGINT NOT NULL DEFAULT 0,
    sink_bytes BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    config_snapshot CLOB,
    stale_since TIMESTAMP,
    last_poll_error VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_run_job FOREIGN KEY (job_id) REFERENCES sync_job(id)
);
CREATE INDEX idx_sync_run_job ON sync_run(job_id);
CREATE INDEX idx_sync_run_status ON sync_run(status);
CREATE TABLE sync_run_event (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_event_run FOREIGN KEY (run_id) REFERENCES sync_run(id)
);
CREATE INDEX idx_sync_run_event_run ON sync_run_event(run_id, created_at);
