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

    @Test
    void rejectsBlankBlockContentBeforeSemanticReview() {
        ConversationDraftValidationResult result = validator.validate(
                draft(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL, "  ", List.of(), List.of())),
                ConversationAnswerScope.GENERAL,
                PortfolioGroundingContext.empty());

        assertThat(result.getFailureCode()).isEqualTo("EMPTY_BLOCK_CONTENT");
        verifyNoInteractions(modelPort);
    }

    @Test
    void rejectsTitleBeyondOneHundredTwentyUnicodeCharacters() {
        ConversationDraft draft = new ConversationDraft(
                "问".repeat(121),
                AnswerResolution.ANSWERED,
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL, "answer", List.of(), List.of())));

        ConversationDraftValidationResult result = validator.validate(
                draft, ConversationAnswerScope.GENERAL, PortfolioGroundingContext.empty());

        assertThat(result.getFailureCode()).isEqualTo("TITLE_TOO_LONG");
        verifyNoInteractions(modelPort);
    }

    @Test
    void rejectsMoreThanEightBlocks() {
        ConversationAnswerBlock block = new ConversationAnswerBlock(
                ConversationSourceScope.GENERAL, "answer", List.of(), List.of());
        ConversationDraft draft = new ConversationDraft(
                "title", AnswerResolution.ANSWERED,
                java.util.Collections.nCopies(9, block));

        ConversationDraftValidationResult result = validator.validate(
                draft, ConversationAnswerScope.GENERAL, PortfolioGroundingContext.empty());

        assertThat(result.getFailureCode()).isEqualTo("TOO_MANY_BLOCKS");
        verifyNoInteractions(modelPort);
    }

    @Test
    void rejectsIndividualAndTotalContentBudgets() {
        ConversationAnswerBlock oversized = new ConversationAnswerBlock(
                ConversationSourceScope.GENERAL, "a".repeat(2001), List.of(), List.of());
        assertThat(validator.validate(
                draft(oversized),
                ConversationAnswerScope.GENERAL,
                PortfolioGroundingContext.empty()).getFailureCode())
                .isEqualTo("BLOCK_TOO_LONG");

        ConversationAnswerBlock twoThousand = new ConversationAnswerBlock(
                ConversationSourceScope.GENERAL, "a".repeat(2000), List.of(), List.of());
        ConversationDraft totalOversized = new ConversationDraft(
                "title", AnswerResolution.ANSWERED,
                java.util.Collections.nCopies(5, twoThousand));
        assertThat(validator.validate(
                totalOversized,
                ConversationAnswerScope.GENERAL,
                PortfolioGroundingContext.empty()).getFailureCode())
                .isEqualTo("ANSWER_TOO_LONG");
        verifyNoInteractions(modelPort);
    }

    private ConversationDraft draft(ConversationAnswerBlock block) {
        return new ConversationDraft(
                "title",
                AnswerResolution.ANSWERED,
                List.of(block));
    }
}
