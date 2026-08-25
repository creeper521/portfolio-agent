package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * 目标主体引用：目标提案对某个公开主体的定位。
 *
 * <p>basis 记录引用来源；EXPLICIT_INPUT 必须携带指向访客原文的
 * {@link UserGoalProposal.InputAnchor}。RESULT 类别表示对推荐结果项的间接
 * 引用，仅限服务端续接路径内部使用，模型提案与语义状态持久化都会拒绝它。</p>
 */
public final class GoalSubjectReference {

    private final Kind kind;
    private final String reference;
    private final Basis basis;
    private final UserGoalProposal.InputAnchor anchor;

    public GoalSubjectReference(
            Kind kind,
            String reference,
            Basis basis,
            UserGoalProposal.InputAnchor anchor) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (reference == null || reference.isBlank() || reference.length() > 128) {
            throw new IllegalArgumentException("subject reference is required and bounded");
        }
        this.reference = reference;
        this.basis = Objects.requireNonNull(basis, "basis");
        if (basis == Basis.EXPLICIT_INPUT && anchor == null) {
            throw new IllegalArgumentException("explicit subject requires an input anchor");
        }
        this.anchor = anchor;
    }

    public Kind getKind() {
        return kind;
    }

    public String getReference() {
        return reference;
    }

    public Basis getBasis() {
        return basis;
    }

    public Optional<UserGoalProposal.InputAnchor> getAnchor() {
        return Optional.ofNullable(anchor);
    }

    /** 主体类别：项目、案例、推荐结果项（仅服务端续接路径可用）。 */
    public enum Kind { PROJECT, CASE, RESULT }
    /** 引用依据：显式输入（须带锚点）、界面提示、会话延续、最近 Turn。 */
    public enum Basis { EXPLICIT_INPUT, SURFACE_HINT, CONTINUATION, RECENT_TURN }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GoalSubjectReference that)) return false;
        return kind == that.kind && reference.equals(that.reference)
                && basis == that.basis && Objects.equals(anchor, that.anchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, reference, basis, anchor);
    }
}
