package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.GroundingReview;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationDraftValidatorTest {

    private final ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
    private final ConversationDraftValidator validator =
            new ConversationDraftValidator(modelPort);

    @Test
    void rejectsGeneralBlockThatCarriesPortfolioReferences() {
        ConversationDraftValidationResult result = validator.validate(
                draft(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL,
                        "general explanation",
                        List.of("claim-1"),
                        List.of("evidence-1"))),
                ConversationAnswerScope.GENERAL,
                PortfolioGroundingContext.empty());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("UNEXPECTED_GENERAL_REFERENCES");
        verifyNoInteractions(modelPort);
    }

    @Test
    void rejectsPortfolioBlockWithoutBothClaimAndEvidence() {
        ConversationDraftValidationResult result = validator.validate(
                draft(new ConversationAnswerBlock(
                        ConversationSourceScope.PORTFOLIO,
                        "portfolio statement",
                        List.of("claim-1"),
                        List.of())),
                ConversationAnswerScope.PORTFOLIO,
                PortfolioGroundingContext.empty());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("MISSING_PORTFOLIO_REFERENCES");
        verifyNoInteractions(modelPort);
    }

    @Test
    void acceptsDeterministicallyValidBlocksAfterSemanticReview() {
        when(modelPort.review(any(), any())).thenReturn(
                ConversationModelResult.success(
                        new GroundingReview(List.of(), List.of())));
        ConversationAnswerBlock block = new ConversationAnswerBlock(
                ConversationSourceScope.GENERAL,
                "general explanation",
                List.of(),
                List.of());

        ConversationDraftValidationResult result = validator.validate(
                draft(block),
                ConversationAnswerScope.GENERAL,
                PortfolioGroundingContext.empty());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getAcceptedBlocks()).containsExactly(block);
    }

    private ConversationDraft draft(ConversationAnswerBlock block) {
        return new ConversationDraft(
                "title",
                AnswerResolution.ANSWERED,
                List.of(block));
    }
}
