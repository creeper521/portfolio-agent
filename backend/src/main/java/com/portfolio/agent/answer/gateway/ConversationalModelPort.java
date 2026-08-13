package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.GroundingReview;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;

import java.util.List;

public interface ConversationalModelPort {

    ConversationModelResult<ConversationRoute> classify(
            String question,
            ConversationWindow window,
            List<ConversationSubjectOption> publicSubjects);

    ConversationModelResult<ConversationDraft> generate(
            String question,
            ConversationWindow window,
            ConversationRoute route,
            PortfolioGroundingContext grounding);

    ConversationModelResult<GroundingReview> review(
            List<ConversationAnswerBlock> blocks,
            PortfolioGroundingContext grounding);

    ConversationModelResult<List<ConversationSuggestedQuestion>> suggest(
            ConversationRoute route,
            ConversationWindow window,
            List<ConversationAnswerBlock> acceptedBlocks,
            List<ConversationSubjectOption> publicSubjects);
}
