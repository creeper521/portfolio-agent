package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;

/** Converts approved evidence metadata into safe public references. */
public final class PublicReferenceValidator {
    public PublicSourceReference validate(PublicEvidenceDescriptor evidence) {
        if (!"APPROVED".equals(evidence.getPublicStatus())) {
            throw new IllegalArgumentException("EVIDENCE_NOT_APPROVED");
        }
        return new PublicSourceReference(
                evidence.getEvidenceCode(), evidence.getLabel(), evidence.getContentVersion(),
                evidence.getSourceType(),
                evidence.getSubjectRoute(), evidence.getEvidenceRoute());
    }
}
