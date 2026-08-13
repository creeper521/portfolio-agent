ALTER TABLE evidence
    ADD COLUMN public_code varchar(80);

UPDATE evidence
SET public_code = stable_id
WHERE public_code IS NULL;

ALTER TABLE evidence
    ALTER COLUMN public_code SET NOT NULL;

CREATE UNIQUE INDEX evidence_release_public_code_uq
    ON evidence(release_id, public_code);
