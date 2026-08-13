package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.GroundingReview;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;

import java.util.List;

/**
 * Deterministic mock of the production model seam, used by the provider eval
 * layer unless an explicit real-provider authorization is granted. It never
 * invokes any external model: every method returns a fixed, closed
 * classification/draft built from the input alone, with no claim or evidence
 * references (nothing is fabricated).
 */
public final class MockConversationalModelPort implements ConversationalModelPort {

    private static final String MOCK_CONTENT = "（评测 Mock 回答）";

    @Override
    public ConversationModelResult<ConversationRoute> classify(
            String question,
            ConversationWindow window,
            List<ConversationSubjectOption> publicSubjects) {
        return ConversationModelResult.success(new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                com.portfolio.agent.answer.domain.ConversationAnswerScope.PORTFOLIO,
                1.0d, null, null, null, false));
    }

    @Override
    public ConversationModelResult<ConversationDraft> generate(
            String question,
            ConversationWindow window,
            ConversationRoute route,
            PortfolioGroundingContext grounding) {
        ConversationAnswerBlock block = new ConversationAnswerBlock(
                ConversationSourceScope.GENERAL,
                MOCK_CONTENT,
                List.of(),
                List.of());
        return ConversationModelResult.success(new ConversationDraft(
                "评测 Mock 回答", com.portfolio.agent.answer.domain.AnswerResolution.ANSWERED,
                List.of(block)));
    }

    @Override
    public ConversationModelResult<GroundingReview> review(
            List<ConversationAnswerBlock> blocks,
            PortfolioGroundingContext grounding) {
        return ConversationModelResult.success(new GroundingReview(
                List.of(), List.of()));
    }

    @Override
    public ConversationModelResult<List<ConversationSuggestedQuestion>> suggest(
            ConversationRoute route,
            ConversationWindow window,
            List<ConversationAnswerBlock> acceptedBlocks,
            List<ConversationSubjectOption> publicSubjects) {
        return ConversationModelResult.success(List.of());
    }
}
