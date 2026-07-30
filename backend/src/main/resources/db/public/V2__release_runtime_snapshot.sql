CREATE TABLE release_runtime_snapshot (
    release_id uuid PRIMARY KEY REFERENCES content_release(release_id) ON DELETE CASCADE,
    payload jsonb NOT NULL,
    payload_checksum char(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT release_runtime_snapshot_checksum_check
        CHECK (payload_checksum ~ '^[0-9a-f]{64}$')
);
