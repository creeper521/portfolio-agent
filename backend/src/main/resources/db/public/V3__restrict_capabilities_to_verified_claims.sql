CREATE TEMPORARY TABLE capability_projection_v3 (
    release_id uuid NOT NULL,
    subject_stable_id varchar(80) NOT NULL,
    capability_code varchar(80) NOT NULL,
    supporting_claim_stable_id varchar(80) NOT NULL,
    PRIMARY KEY (release_id, subject_stable_id, capability_code)
) ON COMMIT DROP;

INSERT INTO capability_projection_v3 (
    release_id,
    subject_stable_id,
    capability_code,
    supporting_claim_stable_id
)
SELECT
    snapshot.release_id,
    claim_item.value ->> 'subjectId',
    upper(btrim(topic.value)),
    min(claim_item.value ->> 'id')
FROM release_runtime_snapshot AS snapshot
CROSS JOIN LATERAL jsonb_array_elements(
    coalesce(snapshot.payload #> '{content,claims}', '[]'::jsonb)
) AS claim_item(value)
CROSS JOIN LATERAL jsonb_array_elements_text(
    coalesce(claim_item.value -> 'topics', '[]'::jsonb)
) AS topic(value)
JOIN claim AS normalized_claim
  ON normalized_claim.release_id = snapshot.release_id
 AND normalized_claim.stable_id = claim_item.value ->> 'id'
 AND normalized_claim.subject_stable_id = claim_item.value ->> 'subjectId'
 AND normalized_claim.verification_status = 'VERIFIED'
WHERE claim_item.value ->> 'verificationStatus' = 'VERIFIED'
  AND btrim(topic.value) <> ''
GROUP BY
    snapshot.release_id,
    claim_item.value ->> 'subjectId',
    upper(btrim(topic.value));

INSERT INTO capability_projection_v3 (
    release_id,
    subject_stable_id,
    capability_code,
    supporting_claim_stable_id
)
SELECT
    capability.release_id,
    capability.subject_stable_id,
    upper(btrim(capability.capability_code)),
    min(capability.supporting_claim_stable_id)
FROM subject_capability AS capability
JOIN claim AS supporting_claim
  ON supporting_claim.release_id = capability.release_id
 AND supporting_claim.stable_id = capability.supporting_claim_stable_id
 AND supporting_claim.subject_stable_id = capability.subject_stable_id
 AND supporting_claim.verification_status = 'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM release_runtime_snapshot AS snapshot
    WHERE snapshot.release_id = capability.release_id
)
  AND btrim(capability.capability_code) <> ''
GROUP BY
    capability.release_id,
    capability.subject_stable_id,
    upper(btrim(capability.capability_code));

DELETE FROM subject_capability;

INSERT INTO subject_capability (
    release_id,
    subject_stable_id,
    capability_code,
    supporting_claim_stable_id
)
SELECT
    release_id,
    subject_stable_id,
    capability_code,
    supporting_claim_stable_id
FROM capability_projection_v3;
