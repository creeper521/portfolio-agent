package com.portfolio.agent.answer.intelligence.execution.validation;

import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicReferenceValidatorTest {

    @Test
    void promotesOnlyApprovedEvidenceToAStablePublicReference() {
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                "evidence-a", "public-v1", "APPROVED", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/project-a", "/evidence/evidence-a", LocalDate.of(2026, 12, 31));

        PublicSourceReference reference = new PublicReferenceValidator().validate(evidence);

        assertEquals("evidence-a", reference.getReferenceKey());
        assertEquals("/evidence/evidence-a", reference.getEvidenceRoute());
    }

    @Test
    void rejectsEvidenceThatIsNotApproved() {
        PublicEvidenceDescriptor evidence = new PublicEvidenceDescriptor(
                "evidence-a", "public-v1", "INTERNAL", PublicEvidenceDescriptor.SourceType.CODE,
                "/projects/project-a", "/evidence/evidence-a", null);

        assertThrows(IllegalArgumentException.class,
                () -> new PublicReferenceValidator().validate(evidence));
    }
}
