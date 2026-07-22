package com.portfolio.agent.answer.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalModelContractTest {

    @Test
    void exposesOnlyTheApprovedDecisionAndModeEnums() {
        assertThat(RetrievalDecisionType.values()).containsExactly(
                RetrievalDecisionType.SUFFICIENT,
                RetrievalDecisionType.INSUFFICIENT,
                RetrievalDecisionType.AMBIGUOUS,
                RetrievalDecisionType.CONFLICTING,
                RetrievalDecisionType.OUT_OF_SCOPE);
        assertThat(RetrievalMode.values()).containsExactly(
                RetrievalMode.HYBRID_ENABLED,
                RetrievalMode.KEYWORD_ONLY,
                RetrievalMode.KEYWORD_FALLBACK);
    }

    @Test
    void policyFixesTheFirstReleaseBudgetsInOneVersionedObject() {
        RetrievalPolicy policy = RetrievalPolicy.firstRelease();

        assertThat(policy.getVersion()).isEqualTo("retrieval-policy-v1");
        assertThat(policy.getKeywordTopK()).isEqualTo(8);
        assertThat(policy.getVectorTopK()).isEqualTo(8);
        assertThat(policy.getMaxChunks()).isEqualTo(12);
        assertThat(policy.getMaxClaims()).isEqualTo(8);
        assertThat(policy.getRrfK()).isEqualTo(60);
    }

    @Test
    void requestLocalModelsDoNotExposeIdentityOrCustomStringRendering() {
        assertSafeShape(EmbeddingVector.class);
        assertSafeShape(RetrievalCandidate.class);
        assertSafeShape(RetrievalDecision.class);
    }

    private void assertSafeShape(Class<?> type) {
        assertThat(type.isRecord()).isFalse();
        assertThat(type.getDeclaredFields())
                .extracting(field -> field.getName().toLowerCase())
                .noneMatch(name -> name.contains("question")
                        || name.contains("turnid")
                        || name.contains("requestid")
                        || name.contains("handoffid"));
        assertThat(Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain("toString");
    }
}
