package com.portfolio.agent.turn.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 语义计划编译器：把 {@link UserGoalProposal} 编译为通过校验的 {@link SemanticTurnPlan}。
 *
 * <p>位于 Goal → Plan 阶段。先逐目标检查公开主体覆盖与目标形状，再生成
 * UserGoal、SemanticTask 与 TaskDependency（跨域目标展开为"通用解释 +
 * 作品集事实 + 跨域综合"三任务 DAG），最后交 {@link SemanticPlanValidator}
 * 校验不变量。公开主体缺失返回 CLARIFICATION_REQUIRED；形状或计划不变量
 * 不满足返回 REJECTED，均不抛出异常。</p>
 */
public final class SemanticPlanCompiler {
    private final SemanticPlanValidator validator;

    public SemanticPlanCompiler(SemanticPlanValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /** 编译便捷入口：未指定受众画像时按访客（GUEST）编译。 */
    public PlanCompilationResult compile(
            UserGoalProposal proposal,
            String contentReleaseId,
            GoalResolutionContext context) {
        return compile(proposal, contentReleaseId, context,
                SemanticTaskParameters.AudienceProfile.GUEST);
    }

    /**
     * 把目标提案编译为语义计划。
     *
     * <p>目标 ID 与任务 ID 按提案顺序确定性生成（goal-N / task-goal-N）；
     * 编译产物经 {@link SemanticPlanValidator} 校验后包装返回。</p>
     *
     * @param contentReleaseId 计划锁定的内容发布 ID
     * @param audienceProfile 本轮 Turn 的受众画像
     * @return 编译成功、需要澄清（存在非公开主体）或被拒绝（形状/不变量不满足）
     */
    public PlanCompilationResult compile(
            UserGoalProposal proposal,
            String contentReleaseId,
            GoalResolutionContext context,
            SemanticTaskParameters.AudienceProfile audienceProfile) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(audienceProfile, "audienceProfile");
        for (UserGoalProposal.ProposedGoal goal : proposal.getGoals()) {
            if (!subjectsArePublic(goal, context)) {
                return PlanCompilationResult.clarificationRequired("PUBLIC_SUBJECT_REQUIRED");
            }
            if (!shapeIsSupported(goal)) {
                return PlanCompilationResult.rejected("GOAL_SHAPE_UNSUPPORTED");
            }
        }

        List<UserGoal> goals = new ArrayList<>();
        List<SemanticTask> tasks = new ArrayList<>();
        List<TaskDependency> dependencies = new ArrayList<>();
        for (int index = 0; index < proposal.getGoals().size(); index++) {
            UserGoalProposal.ProposedGoal proposed = proposal.getGoals().get(index);
            String goalId = "goal-" + (index + 1);
            String fulfillmentTaskId = "task-" + goalId;
            goals.add(new UserGoal(
                    goalId, safeGoalLabel(proposed.getGoalKind()), proposed.getGoalKind(),
                    proposed.getSubjectCandidates(), proposed.getRequestedOutputs(), fulfillmentTaskId));
            if (proposed.getGoalKind() == GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
                compileCrossDomain(proposed, fulfillmentTaskId, tasks, dependencies,
                        audienceProfile);
            } else {
                tasks.add(SemanticTask.of(
                        fulfillmentTaskId, taskType(proposed.getGoalKind()),
                        new SemanticTaskParameters(
                                proposed.getGoalKind(), proposed.getParameters(),
                                proposed.getSubjectCandidates(), audienceProfile),
                        proposed.getRequestedOutputs()));
            }
        }
        SemanticTurnPlan plan = new SemanticTurnPlan(
                contentReleaseId, List.copyOf(goals), List.copyOf(tasks), List.copyOf(dependencies));
        try {
            return PlanCompilationResult.compiled(validator.validate(plan));
        } catch (IllegalArgumentException invalidPlan) {
            return PlanCompilationResult.rejected("PLAN_INVARIANT_VIOLATION");
        }
    }

    /**
     * 编译跨域目标（概念关联到项目）为三任务 DAG。
     *
     * <p>把 ApplyConceptParameters 拆成"通用概念解释"与"作品集单侧面事实"
     * 两个前置任务，再加一个 CROSS_DOMAIN_SYNTHESIS 汇总任务；两条依赖边
     * 指向汇总任务。</p>
     */
    private void compileCrossDomain(
            UserGoalProposal.ProposedGoal proposed,
            String fulfillmentTaskId,
            List<SemanticTask> tasks,
            List<TaskDependency> dependencies,
            SemanticTaskParameters.AudienceProfile audienceProfile) {
        UserGoalProposal.ApplyConceptParameters parameters =
                (UserGoalProposal.ApplyConceptParameters) proposed.getParameters();
        String generalTaskId = fulfillmentTaskId + "-general";
        String portfolioTaskId = fulfillmentTaskId + "-portfolio";
        UserGoalProposal.GeneralExplanationParameters generalParameters =
                new UserGoalProposal.GeneralExplanationParameters(
                        parameters.getConceptAnchor(), parameters.getDepth());
        UserGoalProposal.PortfolioFactParameters portfolioParameters =
                new UserGoalProposal.PortfolioFactParameters(
                        Set.of(parameters.getPortfolioFacet()), parameters.getDepth());
        tasks.add(SemanticTask.of(generalTaskId, SemanticTask.Type.GENERAL_EXPLANATION,
                new SemanticTaskParameters(
                        GoalKind.GENERAL_EXPLANATION, generalParameters, List.of(),
                        audienceProfile)));
        tasks.add(SemanticTask.of(portfolioTaskId, SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(
                        GoalKind.PORTFOLIO_FACT, portfolioParameters,
                        proposed.getSubjectCandidates(), audienceProfile)));
        tasks.add(SemanticTask.of(fulfillmentTaskId, SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS,
                new SemanticTaskParameters(
                        proposed.getGoalKind(), proposed.getParameters(),
                        proposed.getSubjectCandidates(), audienceProfile)));
        dependencies.add(new TaskDependency(generalTaskId, fulfillmentTaskId));
        dependencies.add(new TaskDependency(portfolioTaskId, fulfillmentTaskId));
    }

    /**
     * 判断目标的全部主体是否都在公开主体目录中。
     *
     * <p>RESULT 类别的主体是推荐结果项形式的间接引用，不在此校验，
     * 由续接路径单独保证其合法性。</p>
     */
    private boolean subjectsArePublic(
            UserGoalProposal.ProposedGoal goal,
            GoalResolutionContext context) {
        for (GoalSubjectReference subject : goal.getSubjectCandidates()) {
            if (subject.getKind() == GoalSubjectReference.Kind.RESULT) continue;
            boolean found = context.getPublicSubjects().stream().anyMatch(descriptor ->
                    descriptor.getKind() == subject.getKind()
                            && descriptor.getReference().equals(subject.getReference()));
            if (!found) return false;
        }
        return true;
    }

    /** 按目标类别校验主体数量形状：事实/关联单主体，比较 2..5，推荐与通用类无主体。 */
    private boolean shapeIsSupported(UserGoalProposal.ProposedGoal goal) {
        int subjects = goal.getSubjectCandidates().size();
        return switch (goal.getGoalKind()) {
            case PORTFOLIO_FACT -> subjects == 1;
            case PORTFOLIO_COMPARE -> subjects >= 2 && subjects <= 5;
            case PORTFOLIO_RECOMMEND -> subjects == 0;
            case GENERAL_EXPLANATION, GENERAL_COMPARISON -> subjects == 0;
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> subjects == 1
                    && goal.getSubjectCandidates().get(0).getKind() != GoalSubjectReference.Kind.RESULT;
        };
    }

    /** 目标类别到任务类型的固定映射（跨域目标由 compileCrossDomain 单独展开）。 */
    private SemanticTask.Type taskType(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT -> SemanticTask.Type.PORTFOLIO_FACT;
            case PORTFOLIO_COMPARE -> SemanticTask.Type.PORTFOLIO_COMPARE;
            case PORTFOLIO_RECOMMEND -> SemanticTask.Type.PORTFOLIO_RECOMMEND;
            case GENERAL_EXPLANATION -> SemanticTask.Type.GENERAL_EXPLANATION;
            case GENERAL_COMPARISON -> SemanticTask.Type.GENERAL_COMPARISON;
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO ->
                    SemanticTask.Type.CROSS_DOMAIN_SYNTHESIS;
        };
    }

    /** 服务端固定的目标展示标签，不依赖模型输出。 */
    private String safeGoalLabel(GoalKind kind) {
        return switch (kind) {
            case PORTFOLIO_FACT -> "作品集事实";
            case PORTFOLIO_COMPARE -> "项目比较";
            case PORTFOLIO_RECOMMEND -> "项目推荐";
            case GENERAL_EXPLANATION -> "通用概念说明";
            case GENERAL_COMPARISON -> "通用概念比较";
            case APPLY_GENERAL_CONCEPT_TO_PORTFOLIO -> "概念与项目关联";
        };
    }
}
