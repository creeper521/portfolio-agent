package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.List;

/**
 * 目标解释输入工厂：从 Ask 命令与解析上下文组装 {@link GoalInterpretationInput}。
 */
public final class GoalInterpretationInputFactory {

    public GoalInterpretationInput create(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context) {
        return create(command, context, null);
    }

    /**
     * 组装标准模式的解释输入。
     *
     * <p>会话窗口消息折叠为 "ROLE:text" 摘要；主体提示解析为默认主体；
     * 受众角色映射为 AudienceProfile，缺省 GUEST；仅允许 STANDARD_GOAL 与
     * NEEDS_CLARIFICATION 两条路由。</p>
     *
     * @param recentSemanticState 会话最近语义状态，可为 null
     * @throws IllegalArgumentException 命令输入不是自由文本
     */
    public GoalInterpretationInput create(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            throw new IllegalArgumentException("only free text uses goal interpretation");
        }
        List<String> recentMessages = command.getConversationWindow().getMessages().stream()
                .map(message -> message.getRole().name() + ":" + message.getText())
                .toList();
        return new GoalInterpretationInput(
                freeText.getText(), recentMessages,
                context.getPublicSubjects(), context.getAllowedGoalKinds(),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE, null, List.of(),
                java.util.Set.of(
                        SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION),
                context.getAllowedRecommendationConstraints(),
                context.resolveHint(command.getSurfaceContext().getSubjectHint()),
                command.getSurfaceContext().getAudienceRole()
                        .map(value -> SemanticTaskParameters.AudienceProfile.valueOf(
                        value.name()))
                        .orElse(SemanticTaskParameters.AudienceProfile.GUEST),
                recentSemanticState);
    }
}
