package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * 确定性零目标策略：只处理可由封闭字符集合证明为低信息的标准自由文本。
 *
 * <p>该策略不是通用意图分类器。只要存在 typed recent state、候选路由、
 * 讨论状态或闭集外字符，就把输入交还既有语义解释路径。</p>
 */
public final class UnresolvedIntentPolicy {
    private static final String GUIDANCE =
            "请说明你想介绍、比较还是推荐项目，例如“给我推荐两个项目”。";

    /**
     * 尝试把无 typed 上下文的低信息输入收敛为服务端固定对话结果。
     *
     * @return 命中封闭规则时返回 SERVER_FIXED CONVERSATIONAL；否则放行
     */
    public Optional<ResolvedGoalSet> tryResolve(GoalInterpretationInput input) {
        Objects.requireNonNull(input, "input");
        if (input.getInterpretationMode()
                != GoalInterpretationInput.InterpretationMode.STANDARD
                || input.getDiscussionState()
                != GoalInterpretationInput.DiscussionState.NONE
                || input.getRecentSemanticState() != null
                || !input.getRouteCandidates().isEmpty()
                || !isClosedLowInformation(input.getUserText())) {
            return Optional.empty();
        }
        return Optional.of(ResolvedGoalSet.conversational(GUIDANCE));
    }

    /**
     * 逐 code point 校验空白、十进制数字和 Java 七种标点类别的并集。
     * Symbol、字母和汉字均不在闭集内。
     */
    private boolean isClosedLowInformation(String value) {
        return value.codePoints().allMatch(codePoint ->
                Character.isWhitespace(codePoint)
                        || Character.isDigit(codePoint)
                        || isPunctuation(codePoint));
    }

    /** Java Character 中全部七种 P* 标点类别。 */
    private boolean isPunctuation(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }
}
