package com.portfolio.agent.answer.context.domain;

import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConversationContextDomainTest {
    @Test
    void opaqueTokensHaveFixedEntropyAndRedactedDiagnostics() {
        ResumeToken token = ResumeToken.issue();
        ContextHandle handle = ContextHandle.issue();
        assertEquals(ResumeToken.BYTE_LENGTH, token.copyBytes().length);
        assertNotEquals(token.asBase64Url(), ResumeToken.issue().asBase64Url());
        assertFalse(token.toString().contains(token.asBase64Url()));
        assertFalse(handle.toString().contains(handle.asBase64Url()));
    }

    @Test
    void contextsContainTypedScopeStateOnly() {
        RecentSemanticTaskContext recent = new RecentSemanticTaskContext(
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                List.of(SubjectReference.project("project-a", "public-v1")),
                Set.of("IMPLEMENTATION"), Set.of(), "public-v1", "task-1");
        RecommendationContext recommendation = new RecommendationContext(
                AuthorizedSubjectScope.allPublishedCandidates("public-v1"), "recommendation-v1",
                Set.of("PUBLIC_DELIVERY_EVIDENCE"), Set.of(), Set.of("JAVA"), Set.of(), 3, null);

        assertEquals("public-v1", recent.getContentVersion());
        assertEquals(3, recommendation.getResultLimit());
        assertFalse(recommendation.toString().contains("PUBLIC_DELIVERY_EVIDENCE"));
    }
}
