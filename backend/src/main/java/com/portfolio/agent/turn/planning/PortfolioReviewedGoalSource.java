package com.portfolio.agent.turn.planning;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioReviewedGoalSource implements ReviewedGoalSource {
    private final PortfolioKnowledgeGateway knowledgeGateway;

    public PortfolioReviewedGoalSource(PortfolioKnowledgeGateway knowledgeGateway) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
    }

    @Override
    public UserGoalProposal resolve(AgentTurnCommand command) {
        if (!(command instanceof AgentTurnCommand.Ask ask)
                || !(ask.getInput() instanceof AgentTurnCommand.Preset preset)) {
            throw new ReviewedGoalUnavailableException("reviewed continuation state is unavailable");
        }
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        Match match = find(content, preset.getPresetId());
        if (match == null || !match.question().isActiveContract()
                || !preset.getPresetRevision().equals(match.question().getContractVersion())) {
            throw new ReviewedGoalUnavailableException("preset contract is unavailable");
        }
        UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(
                match.question().getCanonicalQuestion(), 0);
        GoalSubjectReference.Kind subjectKind = match.knowledge().getSubjectType()
                == AnswerSubjectType.PROJECT
                ? GoalSubjectReference.Kind.PROJECT : GoalSubjectReference.Kind.CASE;
        GoalSubjectReference subject = new GoalSubjectReference(
                subjectKind, match.knowledge().getStableId(),
                GoalSubjectReference.Basis.CONTINUATION, null);
        Set<UserGoalProposal.Facet> facets = facets(match.question());
        UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                "preset-goal", GoalKind.PORTFOLIO_FACT, anchor, List.of(subject),
                Set.of(GoalRequestedOutput.OVERVIEW),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioFactParameters(facets));
        return new UserGoalProposal(List.of(goal));
    }

    private Match find(RuntimeAnswerContent content, String presetId) {
        for (AnswerKnowledge knowledge : content.getProjects()) {
            for (AnswerQuestion question : knowledge.getQuestions()) {
                if (presetId.equals(question.getId())) return new Match(knowledge, question);
            }
        }
        for (AnswerKnowledge knowledge : content.getCases()) {
            for (AnswerQuestion question : knowledge.getQuestions()) {
                if (presetId.equals(question.getId())) return new Match(knowledge, question);
            }
        }
        return null;
    }

    private Set<UserGoalProposal.Facet> facets(AnswerQuestion question) {
        Set<UserGoalProposal.Facet> facets = new LinkedHashSet<>();
        for (AnswerClaimCategory category : question.getPreferredClaimCategories()) {
            switch (category) {
                case BACKGROUND -> facets.add(UserGoalProposal.Facet.BACKGROUND);
                case RESPONSIBILITY -> facets.add(UserGoalProposal.Facet.RESPONSIBILITY);
                case VERIFICATION -> facets.add(UserGoalProposal.Facet.VERIFICATION);
                case OUTCOME, LIMITATION -> facets.add(UserGoalProposal.Facet.STATUS);
                default -> facets.add(UserGoalProposal.Facet.SOLUTION);
            }
        }
        if (facets.isEmpty()) facets.add(UserGoalProposal.Facet.OVERVIEW);
        return Set.copyOf(facets);
    }

    private static final class Match {
        private final AnswerKnowledge knowledge;
        private final AnswerQuestion question;
        private Match(AnswerKnowledge knowledge, AnswerQuestion question) {
            this.knowledge = knowledge;
            this.question = question;
        }
        private AnswerKnowledge knowledge() { return knowledge; }
        private AnswerQuestion question() { return question; }
    }
}
