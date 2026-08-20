package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** A deliberately tiny non-semantic social fast path. */
public final class SafeConversationalFastPath {
    private static final Set<String> GREETINGS =
            Set.of("你好", "您好", "嗨", "hello", "hi");
    private static final Set<String> THANKS =
            Set.of("谢谢", "多谢", "感谢");

    public Optional<ResolvedGoalSet> tryResolve(
            AgentTurnCommand.Ask command) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            return Optional.empty();
        }
        String normalized = normalize(freeText.getText());
        if (GREETINGS.contains(normalized)) {
            return Optional.of(ResolvedGoalSet.conversational(
                    "你好，我可以介绍、比较或推荐公开项目。"));
        }
        if (THANKS.contains(normalized)) {
            return Optional.of(ResolvedGoalSet.conversational(
                    "不客气，我可以继续介绍或比较公开项目。"));
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        StringBuilder normalized = new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .filter(codePoint -> "，。！？!?,.～~".indexOf(codePoint) < 0)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
