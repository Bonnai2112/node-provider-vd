ALTER TABLE nodes
    ADD COLUMN el_sync_kind VARCHAR(20),
    ADD COLUMN el_sync_current_block BIGINT,
    ADD COLUMN el_sync_highest_block BIGINT,
    ADD COLUMN cl_sync_kind VARCHAR(20),
    ADD COLUMN cl_sync_head_slot BIGINT,
    ADD COLUMN cl_sync_distance BIGINT,
    ADD COLUMN peers INTEGER,
    ADD COLUMN last_observed_at TIMESTAMPTZ;
