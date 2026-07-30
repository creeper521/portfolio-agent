CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE content_release (
    release_id uuid PRIMARY KEY,
    release_version varchar(80) NOT NULL UNIQUE,
    schema_version varchar(40) NOT NULL,
    content_hash char(64) NOT NULL,
    status varchar(24) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    verified_at timestamptz,
    published_at timestamptz,
    CONSTRAINT content_release_status_check CHECK (
        status IN ('DRAFT', 'VALIDATED', 'CANONICALIZED', 'APPROVED', 'COMPILED', 'VERIFIED', 'PUBLISHED')
    )
);

CREATE TABLE portfolio_subject (
    release_id uuid NOT NULL REFERENCES content_release(release_id) ON DELETE CASCADE,
    stable_id varchar(80) NOT NULL,
    subject_kind varchar(16) NOT NULL,
    slug varchar(160) NOT NULL,
    title text NOT NULL,
    summary text NOT NULL,
    career_track varchar(40),
    contribution_type varchar(40),
    achievement_status varchar(40),
    verification_status varchar(40),
    public_route text NOT NULL,
    display_order integer NOT NULL,
    PRIMARY KEY (release_id, stable_id),
    UNIQUE (release_id, subject_kind, slug),
    CONSTRAINT portfolio_subject_kind_check CHECK (subject_kind IN ('PROJECT', 'CASE'))
);

CREATE TABLE project_profile (
    release_id uuid NOT NULL,
    stable_id varchar(80) NOT NULL,
    project_status varchar(40) NOT NULL,
    project_nature varchar(40),
    display_tier varchar(40),
    featured_case_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY (release_id, stable_id),
    FOREIGN KEY (release_id, stable_id)
        REFERENCES portfolio_subject(release_id, stable_id) ON DELETE CASCADE
);

CREATE TABLE case_study (
    release_id uuid NOT NULL,
    stable_id varchar(80) NOT NULL,
    project_stable_id varchar(80),
    case_type varchar(40) NOT NULL,
    collection_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY (release_id, stable_id),
    FOREIGN KEY (release_id, stable_id)
        REFERENCES portfolio_subject(release_id, stable_id) ON DELETE CASCADE,
    FOREIGN KEY (release_id, project_stable_id)
        REFERENCES portfolio_subject(release_id, stable_id)
);

CREATE TABLE claim (
    release_id uuid NOT NULL REFERENCES content_release(release_id) ON DELETE CASCADE,
    stable_id varchar(80) NOT NULL,
    subject_stable_id varchar(80) NOT NULL,
    subject_kind varchar(16) NOT NULL,
    category varchar(40) NOT NULL,
    statement text NOT NULL,
    verification_status varchar(40) NOT NULL,
    display_order integer NOT NULL,
    PRIMARY KEY (release_id, stable_id),
    FOREIGN KEY (release_id, subject_stable_id)
        REFERENCES portfolio_subject(release_id, stable_id) ON DELETE CASCADE
);

CREATE TABLE evidence (
    release_id uuid NOT NULL REFERENCES content_release(release_id) ON DELETE CASCADE,
    stable_id varchar(80) NOT NULL,
    evidence_type varchar(40) NOT NULL,
    label text NOT NULL,
    description text NOT NULL,
    public_url text,
    public_status varchar(24) NOT NULL,
    PRIMARY KEY (release_id, stable_id),
    CONSTRAINT evidence_public_status_check CHECK (public_status = 'APPROVED')
);

CREATE TABLE claim_evidence_link (
    release_id uuid NOT NULL REFERENCES content_release(release_id) ON DELETE CASCADE,
    claim_stable_id varchar(80) NOT NULL,
    evidence_stable_id varchar(80) NOT NULL,
    support_type varchar(40) NOT NULL,
    PRIMARY KEY (release_id, claim_stable_id, evidence_stable_id),
    FOREIGN KEY (release_id, claim_stable_id)
        REFERENCES claim(release_id, stable_id) ON DELETE CASCADE,
    FOREIGN KEY (release_id, evidence_stable_id)
        REFERENCES evidence(release_id, stable_id) ON DELETE CASCADE
);

CREATE TABLE subject_capability (
    release_id uuid NOT NULL REFERENCES content_release(release_id) ON DELETE CASCADE,
    subject_stable_id varchar(80) NOT NULL,
    capability_code varchar(80) NOT NULL,
    supporting_claim_stable_id varchar(80) NOT NULL,
    weight numeric(6, 5) NOT NULL DEFAULT 1,
    PRIMARY KEY (release_id, subject_stable_id, capability_code),
    FOREIGN KEY (release_id, subject_stable_id)
        REFERENCES portfolio_subject(release_id, stable_id) ON DELETE CASCADE,
    FOREIGN KEY (release_id, supporting_claim_stable_id)
        REFERENCES claim(release_id, stable_id),
    CONSTRAINT subject_capability_weight_check CHECK (weight > 0 AND weight <= 1)
);

CREATE TABLE retrieval_document (
    release_id uuid NOT NULL REFERENCES content_release(release_id) ON DELETE CASCADE,
    stable_id varchar(120) NOT NULL,
    subject_stable_id varchar(80) NOT NULL,
    claim_stable_id varchar(80) NOT NULL,
    search_text text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(search_text, ''))
    ) STORED,
    embedding vector(512),
    embedding_model varchar(120),
    content_hash char(64) NOT NULL,
    PRIMARY KEY (release_id, stable_id),
    FOREIGN KEY (release_id, subject_stable_id)
        REFERENCES portfolio_subject(release_id, stable_id) ON DELETE CASCADE,
    FOREIGN KEY (release_id, claim_stable_id)
        REFERENCES claim(release_id, stable_id) ON DELETE CASCADE
);

CREATE INDEX retrieval_document_search_idx
    ON retrieval_document USING gin (search_vector);
CREATE INDEX subject_capability_lookup_idx
    ON subject_capability (release_id, capability_code, subject_stable_id);

CREATE TABLE active_release (
    singleton boolean PRIMARY KEY DEFAULT true,
    release_id uuid NOT NULL UNIQUE REFERENCES content_release(release_id),
    activated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT active_release_singleton_check CHECK (singleton)
);
