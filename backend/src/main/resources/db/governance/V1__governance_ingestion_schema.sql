CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE import_run (
    run_id uuid PRIMARY KEY,
    root_fingerprint char(64) NOT NULL,
    mode varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    summary jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT import_run_mode_check CHECK (mode IN ('DRY_RUN', 'IMPORT')),
    CONSTRAINT import_run_status_check CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED'))
);

CREATE TABLE source_document (
    document_id uuid PRIMARY KEY,
    relative_path text NOT NULL UNIQUE,
    current_revision_id uuid,
    lifecycle_status varchar(16) NOT NULL,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT source_document_status_check CHECK (
        lifecycle_status IN ('ACTIVE', 'MISSING', 'BLOCKED')
    )
);

CREATE TABLE source_revision (
    revision_id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES source_document(document_id) ON DELETE CASCADE,
    content_hash char(64) NOT NULL,
    byte_size bigint NOT NULL,
    parse_status varchar(24) NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT now(),
    error_code varchar(80),
    UNIQUE (document_id, content_hash),
    CONSTRAINT source_revision_parse_status_check CHECK (
        parse_status IN ('PARSED', 'VECTOR_PENDING', 'FAILED')
    )
);

ALTER TABLE source_document
    ADD CONSTRAINT source_document_current_revision_fk
    FOREIGN KEY (current_revision_id) REFERENCES source_revision(revision_id);

CREATE TABLE source_chunk (
    chunk_id uuid PRIMARY KEY,
    revision_id uuid NOT NULL REFERENCES source_revision(revision_id) ON DELETE CASCADE,
    ordinal integer NOT NULL,
    chunk_hash char(64) NOT NULL,
    private_text text NOT NULL,
    embedding vector(512),
    embedding_model varchar(120),
    vector_status varchar(24) NOT NULL,
    UNIQUE (revision_id, ordinal),
    CONSTRAINT source_chunk_vector_status_check CHECK (
        vector_status IN ('READY', 'VECTOR_PENDING', 'FAILED')
    )
);

CREATE TABLE source_link_suggestion (
    suggestion_id uuid PRIMARY KEY,
    revision_id uuid NOT NULL REFERENCES source_revision(revision_id) ON DELETE CASCADE,
    target_kind varchar(24) NOT NULL,
    target_stable_id varchar(80),
    suggestion_payload jsonb NOT NULL,
    review_status varchar(24) NOT NULL,
    supporting_chunk_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    CONSTRAINT source_link_suggestion_review_check CHECK (
        review_status IN ('PENDING', 'APPROVED', 'REJECTED')
    )
);

CREATE TABLE import_run_document (
    run_id uuid NOT NULL REFERENCES import_run(run_id) ON DELETE CASCADE,
    relative_path text NOT NULL,
    scan_status varchar(16) NOT NULL,
    document_id uuid REFERENCES source_document(document_id),
    error_code varchar(80),
    PRIMARY KEY (run_id, relative_path),
    CONSTRAINT import_run_document_status_check CHECK (
        scan_status IN ('ADDED', 'CHANGED', 'UNCHANGED', 'MISSING', 'FAILED', 'BLOCKED')
    )
);
