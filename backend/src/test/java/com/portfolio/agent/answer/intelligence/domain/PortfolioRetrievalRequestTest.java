package com.portfolio.agent.answer.intelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioRetrievalRequestTest {

    @Test
    void createsAnExactPresetContractRequestWithoutCategoryPreferences() {
        PortfolioRetrievalRequest request = PortfolioRetrievalRequest.contractScope(
                "Explain the approved project facts", "project-a", List.of("claim-a", "claim-b"));

        assertThat(request.getStrategy()).isEqualTo(PortfolioRetrievalStrategy.PRESET_CONTRACT);
        assertThat(request.isExactPortfolioLookup()).isTrue();
        assertThat(request.getMode()).isEqualTo(PortfolioTaskMode.FACT_LOOKUP);
        assertThat(request.getRequiredPortfolioIds()).containsExactly("project-a");
        assertThat(request.getRequiredClaimIds()).containsExactly("claim-a", "claim-b");
        assertThat(request.getPreferredClaimCategories()).isEmpty();
        assertThat(request.getLimit()).isEqualTo(2);
    }

    @Test
    void rejectsAnEmptyOrInvalidPresetContractScope() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                PortfolioRetrievalRequest.contractScope("question", " ", List.of("claim-a")));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PortfolioRetrievalRequest.contractScope("question", "project-a", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PortfolioRetrievalRequest.contractScope(
                        "question", "project-a", List.of("claim-a", "claim-a")));
    }
}
