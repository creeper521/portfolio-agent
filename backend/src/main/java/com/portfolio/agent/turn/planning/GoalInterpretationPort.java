package com.portfolio.agent.turn.planning;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.turn.execution.TurnDeadline;

/**
 * 目标解释端口：把封闭输入解释为语义路由或纯对话结果。
 *
 * <p>生产实现为模型调用加 fail-closed 模板路径；实现必须保证解释失败时
 * 抛出 {@link GoalInterpretationUnavailableException}，而不是返回编造结果。</p>
 */
public interface GoalInterpretationPort {
    /**
     * 解释目标输入。
     *
     * @param modelExecution Claim 后冻结的模型执行快照；实现必须在被采纳的
     *                      阶段调用其打标方法
     * @throws GoalInterpretationUnavailableException 解释能力不可用
     */
    GoalInterpretationResult interpret(
            GoalInterpretationInput input,
            TurnDeadline deadline,
            ResolvedModelExecution modelExecution);
}
