DELETE FROM subject_capability AS capability
WHERE NOT EXISTS (
    SELECT 1
    FROM claim AS supporting_claim
    WHERE supporting_claim.release_id = capability.release_id
      AND supporting_claim.stable_id = capability.supporting_claim_stable_id
      AND supporting_claim.subject_stable_id = capability.subject_stable_id
      AND supporting_claim.verification_status = 'VERIFIED'
);
