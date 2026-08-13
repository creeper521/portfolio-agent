package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.domain.GroundedAnswerContribution;
import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactResultPolicyTest {
    @Test
    void unsupportedMaterialCarriesOmittedTopicsWithoutInventedStatements() {
        EvidenceSupportAssessment assessment = new EvidenceSupportAssessment(
                EvidenceSupportAssessment.SupportStatus.INSUFFICIENT, Map.of(), List.of("OUTCOME"));
        PortfolioAnswerMaterial material = new FactResultPolicy().material(
                null, assessment, "Project A");
        assertEquals(List.of(), material.getStatements());
        assertEquals(List.of("OUTCOME"), material.getOmittedTopicLabels());
    }
}
