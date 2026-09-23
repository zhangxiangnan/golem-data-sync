ALTER TABLE sync_run ADD COLUMN engine_type VARCHAR(16) NOT NULL DEFAULT 'ZETA';
ALTER TABLE sync_run ADD COLUMN engine_profile_id VARCHAR(80) NOT NULL DEFAULT 'zeta-local';
ALTER TABLE sync_run ADD COLUMN external_job_id VARCHAR(128);
ALTER TABLE sync_run ADD COLUMN tracking_url VARCHAR(1000);
ALTER TABLE sync_run ADD COLUMN metrics_available BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE sync_run SET external_job_id = seatunnel_job_id WHERE external_job_id IS NULL;
CREATE INDEX idx_sync_run_engine_profile ON sync_run(engine_profile_id);
