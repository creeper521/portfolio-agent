package com.portfolio.agent.answer.service;

import java.util.List;
import java.util.Objects;

public final class RetrievalQueryRiskGate {

    private static final List<String> OVERRIDE_ACTIONS = List.of(
            "忽略", "绕过", "泄露", "输出", "发送", "发给", "提供");
    private static final List<String> PROTECTED_TARGETS = List.of(
            "系统提示", "内部路径", "运行环境", "环境变量", "密钥", "凭据",
            "未公开", "私有资料", "私有内容");
    private static final List<String> DESTRUCTIVE_ACTIONS = List.of(
            "删除", "清除", "清理", "重置");
    private static final List<String> UNBOUNDED_SCOPES = List.of(
            "任意", "所有", "全部", "批量");
    private static final List<String> SAFEGUARD_BYPASSES = List.of(
            "不确认", "无需确认", "跳过", "不选环境", "不选择环境", "直接");
    private static final List<String> GUARANTEE_MARKERS = List.of(
            "保证", "确保", "一定", "必然", "直到成功");
    private static final List<String> AUTOMATION_MARKERS = List.of(
            "自动重试", "自动补偿", "重试三次", "最终成功", "直到成功");

    public boolean blocks(NormalizedRetrievalQuery query) {
        String text = Objects.requireNonNull(query, "query").getLocalText();
        return isInstructionOverride(text)
                || isUnboundedDestructiveOperation(text)
                || isUnsupportedGuarantee(text);
    }

    private boolean isInstructionOverride(String text) {
        return containsAny(text, OVERRIDE_ACTIONS)
                && containsAny(text, PROTECTED_TARGETS);
    }

    private boolean isUnboundedDestructiveOperation(String text) {
        return containsAny(text, DESTRUCTIVE_ACTIONS)
                && containsAny(text, UNBOUNDED_SCOPES)
                && containsAny(text, SAFEGUARD_BYPASSES);
    }

    private boolean isUnsupportedGuarantee(String text) {
        return containsAny(text, GUARANTEE_MARKERS)
                && containsAny(text, AUTOMATION_MARKERS);
    }

    private boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
