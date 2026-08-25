package com.portfolio.agent.turn.planning;

import java.util.Objects;

/**
 * Validates model semantics against backend-owned typed scope.
 *
 * <p>语义路由校验器：把模型提出的路由约束在服务端拥有的封闭范围内。
 * 先校验路由与候选键的许可性；STANDARD_GOAL 依次做最近语义主体绑定与
 * 默认主体绑定；CONTINUE_CURRENT_PROJECT 锁定讨论主体；最后统一校验
 * 目标类别、主体公开性与推荐数量。绑定与校验失败均抛出
 * IllegalArgumentException，由上层按模型/非模型路径分别处理。</p>
 */
public final class SemanticRouteValidator {

    /**
     * 校验并补全语义路由提案。
     *
     * @return 经主体绑定与封闭校验后的新提案（原提案不可变）
     * @throws IllegalArgumentException 路由不在允许集合、候选键越界、
     *         绑定前提不满足或目标校验失败
     */
    public SemanticRouteProposal validate(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(input, "input");
        if (!input.getAllowedRoutes().contains(proposal.getRoute())) {
            throw new IllegalArgumentException("semantic route is not allowed");
        }
        proposal.getCandidateKey().ifPresent(candidateKey -> {
            if (input.getRouteCandidates().stream().noneMatch(
                    candidate -> candidate.getCandidateKey().equals(candidateKey))) {
                throw new IllegalArgumentException(
                        "semantic route candidate is outside typed scope");
            }
        });
        SemanticRouteProposal validated = switch (proposal.getRoute()) {
            case CONTINUE_CURRENT_PROJECT -> lockDiscussionGoal(proposal, input);
            case STANDARD_GOAL -> bindDefaultSubject(
                    bindRecentSubject(proposal, input), input);
            default -> proposal;
        };
        validated.getGoalProposal().ifPresent(goalProposal ->
                validateGoals(goalProposal, input));
        return validated;
    }

    /**
     * 按 recentReference 把无主体的作品集目标绑定到最近 Turn 的公开主体。
     *
     * <p>仅在标准模式、无默认主体、单目标且目标未自带主体时生效；引用越出
     * typed 最近状态或形状不符即拒绝。</p>
     */
    private SemanticRouteProposal bindRecentSubject(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        if (input.getDefaultSubject() != null
                || input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.STANDARD
                || proposal.getGoalProposal().isEmpty()) {
            return proposal;
        }
        SemanticRouteProposal.RecentSemanticReference reference =
                proposal.getRecentReference().orElse(null);
        if (reference == null) return proposal;
        GoalInterpretationInput.PublicSubjectDescriptor recent =
                input.recentPortfolioSubject(reference.goalId(), reference.sectionId());
        if (recent == null) {
            throw new IllegalArgumentException(
                    "recent semantic reference is outside typed state");
        }
        UserGoalProposal source = proposal.getGoalProposal().orElseThrow();
        if (source.getGoals().size() != 1) {
            throw new IllegalArgumentException(
                    "recent semantic reference requires exactly one goal");
        }
        UserGoalProposal.ProposedGoal sourceGoal = source.getGoals().getFirst();
        if (!sourceGoal.getSubjectCandidates().isEmpty()
                || sourceGoal.getGoalKind() != GoalKind.PORTFOLIO_FACT
                && sourceGoal.getGoalKind()
                != GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
            throw new IllegalArgumentException(
                    "recent semantic reference cannot override proposed subject");
        }
        java.util.List<UserGoalProposal.ProposedGoal> goals = source.getGoals().stream()
                .map(goal -> bindRecentSubject(goal, recent)).toList();
        return SemanticRouteProposal.standardGoal(new UserGoalProposal(goals), reference);
    }

    /** 给无主体的作品集事实/概念关联目标绑定 RECENT_TURN 依据的主体。 */
    private UserGoalProposal.ProposedGoal bindRecentSubject(
            UserGoalProposal.ProposedGoal goal,
            GoalInterpretationInput.PublicSubjectDescriptor recent) {
        if (!goal.getSubjectCandidates().isEmpty()
                || goal.getGoalKind() != GoalKind.PORTFOLIO_FACT
                && goal.getGoalKind()
                != GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
            return goal;
        }
        return new UserGoalProposal.ProposedGoal(
                goal.getGoalKey(), goal.getGoalKind(), goal.getInputAnchor(),
                java.util.List.of(new GoalSubjectReference(
                        recent.getKind(), recent.getReference(),
                        GoalSubjectReference.Basis.RECENT_TURN, null)),
                goal.getRequestedOutputs(), goal.getKnowledgeRequirement(),
                goal.getParameters());
    }

    /**
     * 无 recentReference 时，把界面主体提示解析出的默认主体绑到无主体的作品集目标。
     *
     * <p>仅标准模式生效；被绑定的目标随后不再携带 recentReference。</p>
     */
    private SemanticRouteProposal bindDefaultSubject(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        GoalInterpretationInput.PublicSubjectDescriptor defaultSubject =
                input.getDefaultSubject();
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.STANDARD
                || defaultSubject == null
                || proposal.getGoalProposal().isEmpty()
                || proposal.getRecentReference().isPresent()) {
            return proposal;
        }
        UserGoalProposal source = proposal.getGoalProposal().orElseThrow();
        java.util.List<UserGoalProposal.ProposedGoal> goals = source.getGoals().stream()
                .map(goal -> bindDefaultSubject(goal, defaultSubject)).toList();
        return SemanticRouteProposal.standardGoal(new UserGoalProposal(goals));
    }

    /** 给无主体的作品集事实/概念关联目标绑定 SURFACE_HINT 依据的主体。 */
    private UserGoalProposal.ProposedGoal bindDefaultSubject(
            UserGoalProposal.ProposedGoal goal,
            GoalInterpretationInput.PublicSubjectDescriptor defaultSubject) {
        if (!goal.getSubjectCandidates().isEmpty()
                || goal.getGoalKind() != GoalKind.PORTFOLIO_FACT
                && goal.getGoalKind() != GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
            return goal;
        }
        return new UserGoalProposal.ProposedGoal(
                goal.getGoalKey(), goal.getGoalKind(), goal.getInputAnchor(),
                java.util.List.of(new GoalSubjectReference(
                        defaultSubject.getKind(), defaultSubject.getReference(),
                        GoalSubjectReference.Basis.SURFACE_HINT, null)),
                goal.getRequestedOutputs(), goal.getKnowledgeRequirement(),
                goal.getParameters());
    }

    /**
     * 把继续讨论路由的目标锁定到会话的锁定主体。
     *
     * <p>要求 DISCUSSION 模式 + 活跃讨论 + 存在锁定主体，且目标不能自带
     * 主体；绑定后主体依据为 CONTINUATION，候选键清空。</p>
     */
    private SemanticRouteProposal lockDiscussionGoal(
            SemanticRouteProposal proposal,
            GoalInterpretationInput input) {
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.DISCUSSION
                || input.getDiscussionState()
                != GoalInterpretationInput.DiscussionState.ACTIVE
                || input.getLockedSubject() == null) {
            throw new IllegalArgumentException(
                    "discussion route requires an active locked subject");
        }
        UserGoalProposal source =
                proposal.getGoalProposal().orElseThrow();
        if (source.getGoals().size() != 1) {
            throw new IllegalArgumentException(
                    "discussion route requires exactly one goal");
        }
        UserGoalProposal.ProposedGoal goal =
                source.getGoals().getFirst();
        if (!goal.getSubjectCandidates().isEmpty()
                || goal.getGoalKind() != GoalKind.PORTFOLIO_FACT
                && goal.getGoalKind()
                != GoalKind.APPLY_GENERAL_CONCEPT_TO_PORTFOLIO) {
            throw new IllegalArgumentException(
                    "discussion goal cannot propose its own subject");
        }
        GoalInterpretationInput.PublicSubjectDescriptor locked =
                input.getLockedSubject();
        UserGoalProposal.ProposedGoal bound =
                new UserGoalProposal.ProposedGoal(
                        goal.getGoalKey(),
                        goal.getGoalKind(),
                        goal.getInputAnchor(),
                        java.util.List.of(
                                new GoalSubjectReference(
                                        locked.getKind(),
                                        locked.getReference(),
                                        GoalSubjectReference.Basis.CONTINUATION,
                                        null)),
                        goal.getRequestedOutputs(),
                        goal.getKnowledgeRequirement(),
                        goal.getParameters());
        return SemanticRouteProposal.discussion(
                proposal.getRoute(), null,
                new UserGoalProposal(java.util.List.of(bound)));
    }

    /**
     * 逐目标校验：类别在允许集合内、主体必须非 RESULT 且全部公开、
     * 推荐数量在 1..5 之间。
     */
    private void validateGoals(
            UserGoalProposal proposal,
            GoalInterpretationInput input) {
        for (UserGoalProposal.ProposedGoal goal : proposal.getGoals()) {
            if (!input.getAllowedGoalKinds().contains(goal.getGoalKind())) {
                throw new IllegalArgumentException(
                        "semantic route goal kind is not allowed");
            }
            for (GoalSubjectReference subject : goal.getSubjectCandidates()) {
                if (subject.getKind() == GoalSubjectReference.Kind.RESULT
                        || !input.containsPublicSubject(
                        subject.getKind(), subject.getReference())) {
                    throw new IllegalArgumentException(
                            "semantic route subject is outside public scope");
                }
            }
            if (goal.getParameters()
                    instanceof UserGoalProposal.PortfolioRecommendationParameters parameters
                    && (parameters.getRequestedSize() < 1
                    || parameters.getRequestedSize() > 5)) {
                throw new IllegalArgumentException(
                        "semantic route recommendation size is invalid");
            }
        }
    }
}
