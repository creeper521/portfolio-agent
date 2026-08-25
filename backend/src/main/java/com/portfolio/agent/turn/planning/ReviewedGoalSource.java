package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

/**
 * 已审核目标源：为非自由文本命令提供来自已审核快照的目标提案。
 */
public interface ReviewedGoalSource {
    /**
     * 解析命令为已审核目标提案。
     *
     * @throws ReviewedGoalUnavailableException 命令形态不支持或审核状态不可用
     */
    UserGoalProposal resolve(AgentTurnCommand command);
}
