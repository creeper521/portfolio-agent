package com.portfolio.agent.answer.routing.gateway;

import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnInterpretationPortTest {

    @Test
    void inputIsBoundedToCurrentInputAndPublicSubjectCatalog() {
        TurnInterpretationPort.TurnInterpretationInput input = new TurnInterpretationPort.TurnInterpretationInput(
                "介绍 project-a", List.of(new SubjectReference(
                SubjectType.PROJECT, "project-a", SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1")),
                Set.of(SemanticTaskType.PORTFOLIO_FACT));

        assertThat(input.getCurrentInput()).isEqualTo("介绍 project-a");
        assertThat(input.getPublicSubjects()).hasSize(1);
        assertThat(input.getAllowedTaskTypes()).containsExactly(SemanticTaskType.PORTFOLIO_FACT);
        assertThatThrownBy(() -> new TurnInterpretationPort.TurnInterpretationInput(
                " ", List.of(), Set.of(SemanticTaskType.PORTFOLIO_FACT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedInterpretationCarriesOnlyAStableFailureCode() {
        TurnInterpretationPort.TurnInterpretationResult result =
                TurnInterpretationPort.TurnInterpretationResult.failure(ConversationModelFailureCode.INVALID_RESPONSE);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getProposal()).isEmpty();
        assertThat(result.getFailureCode()).isEqualTo(ConversationModelFailureCode.INVALID_RESPONSE);
    }
}
