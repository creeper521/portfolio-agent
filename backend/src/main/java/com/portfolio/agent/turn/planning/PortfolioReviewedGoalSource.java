package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerQuestion;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerSubjectType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 已审核目标源：把预设问题命令解析为基于公开知识快照的目标提案。
 *
 * <p>只有 Ask+Preset 命令可解析；预设必须在当前内容发布中处于活跃契约且
 * 修订版本一致，否则抛出 {@link ReviewedGoalUnavailableException}。</p>
 */
public final class PortfolioReviewedGoalSource implements ReviewedGoalSource {
    private final PortfolioKnowledgeGateway knowledgeGateway;

    public PortfolioReviewedGoalSource(PortfolioKnowledgeGateway knowledgeGateway) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
    }

    /**
     * 把预设问题命令解析为 PORTFOLIO_FACT 目标提案。
     *
     * <p>按预设契约的问题类别映射查询侧面；主体引用以 CONTINUATION 依据
     * 绑定到知识条目的稳定 ID；InputAnchor 使用契约的规范问题文本。</p>
     *
     * @throws ReviewedGoalUnavailableException 命令非预设提问，或预设契约
     *         不存在、不活跃或修订版本不匹配
     */
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
        UserGoalProposal.PortfolioFactParameters parameters =
                new UserGoalProposal.PortfolioFactParameters(
                        facets, UserGoalProposal.Depth.STANDARD);
        UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                "preset-goal", GoalKind.PORTFOLIO_FACT, anchor, List.of(subject),
                outputs(facets),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                parameters);
        return new UserGoalProposal(List.of(goal));
    }

    /** 在项目与案例知识中查找预设 ID 对应的问答契约；找不到返回 null。 */
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

    /** 把预设问题的首选声明类别映射为查询侧面；无映射时回退 OVERVIEW。 */
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

    /** 把侧面集合映射为同名的请求输出集合。 */
    private Set<GoalRequestedOutput> outputs(Set<UserGoalProposal.Facet> facets) {
        return facets.stream()
                .map(value -> GoalRequestedOutput.valueOf(value.name()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 预设匹配结果：命中的知识条目与其问答契约。 */
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
