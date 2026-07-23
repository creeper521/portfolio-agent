package com.portfolio.agent.portfolio.service.result;

import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;

import java.util.List;
import java.util.Objects;

public final class CaseDetails {

    private final CaseStudy caseStudy;
    private final List<EvidenceRecord> evidence;
    private final List<String> suggestedQuestions;

    public CaseDetails(
            CaseStudy caseStudy,
            List<EvidenceRecord> evidence,
            List<String> suggestedQuestions
    ) {
        this.caseStudy = caseStudy;
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
    }

    public CaseStudy getCaseStudy() {
        return caseStudy;
    }

    public List<EvidenceRecord> getEvidence() {
        return evidence;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CaseDetails that)) {
            return false;
        }
        return Objects.equals(caseStudy, that.caseStudy)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseStudy, evidence, suggestedQuestions);
    }

    @Override
    public String toString() {
        return "CaseDetails{" +
                "caseStudy=" + caseStudy +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                '}';
    }
}
