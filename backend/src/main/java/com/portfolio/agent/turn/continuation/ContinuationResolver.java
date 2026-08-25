package com.portfolio.agent.turn.continuation;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Explicit handle wins; otherwise exactly one compatible active context is required.
 *
 * <p>续接解析器：从会话的活跃上下文中解析本轮要使用的上下文。显式句柄
 * 优先精确匹配；未指定句柄时必须恰好存在一个同会话、同类型、未过期的
 * 候选，多候选返回 CLARIFICATION_REQUIRED；内容发布不一致返回
 * REBIND_REQUIRED。</p>
 */
public final class ContinuationResolver {
    private final Clock clock;
    public ContinuationResolver(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /**
     * 解析续接上下文。
     *
     * @param explicitHandle 客户端回传的显式句柄，可为 null
     * @param resultItemId 期望进入的推荐结果项 ID，可为 null；仅对
     *                     RECOMMENDATION 上下文有效
     * @return 解析状态与命中上下文（非 RESOLVED/REBIND_REQUIRED 时上下文为 null）
     */
    public Resolution resolve(
            String explicitHandle, String resultItemId,
            ContinuationContext.Kind expectedKind, String conversationId,
            String currentReleaseId, List<ContinuationContext> activeContexts) {
        Objects.requireNonNull(expectedKind, "expectedKind");
        List<ContinuationContext> candidates = List.copyOf(activeContexts).stream()
                .filter(value -> value.getConversationId().equals(conversationId))
                .filter(value -> value.getKind() == expectedKind)
                .filter(value -> clock.instant().isBefore(value.getExpiresAt()))
                .toList();
        ContinuationContext selected;
        if (explicitHandle != null) {
            selected = candidates.stream().filter(value ->
                    value.getContextHandle().equals(explicitHandle)).findFirst().orElse(null);
            if (selected == null) return Resolution.of(Status.NOT_FOUND);
        } else {
            if (candidates.isEmpty()) return Resolution.of(Status.NOT_FOUND);
            if (candidates.size() != 1) return Resolution.of(Status.CLARIFICATION_REQUIRED);
            selected = candidates.getFirst();
        }
        if (!selected.getContentReleaseId().equals(currentReleaseId)) {
            return new Resolution(Status.REBIND_REQUIRED, selected);
        }
        if (resultItemId != null) {
            if (!(selected instanceof ContinuationContext.Recommendation recommendation)
                    || recommendation.getSelectedResults().stream().noneMatch(value ->
                    value.resultItemId().equals(resultItemId))) {
                return Resolution.of(Status.RESULT_ITEM_INVALID);
            }
        }
        return new Resolution(Status.RESOLVED, selected);
    }

    /** 解析结果：状态与命中的上下文。 */
    public record Resolution(Status status, ContinuationContext context) {
        public Resolution { Objects.requireNonNull(status, "status"); }
        static Resolution of(Status status) { return new Resolution(status, null); }
    }
    /** 解析状态：已解析/未找到/需要澄清（多候选）/需重绑（发布不一致）/结果项无效。 */
    public enum Status {
        RESOLVED, NOT_FOUND, CLARIFICATION_REQUIRED, REBIND_REQUIRED, RESULT_ITEM_INVALID
    }
}
