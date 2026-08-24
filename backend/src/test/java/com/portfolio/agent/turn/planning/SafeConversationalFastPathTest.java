package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SafeConversationalFastPathTest {

    private final SafeConversationalFastPath fastPath =
            new SafeConversationalFastPath();

    @Test
    void resolvesOnlyTheSmallSafeSocialSurface() {
        Optional<ResolvedGoalSet> result = fastPath.tryResolve(ask("你好！"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getKind())
                .isEqualTo(ResolvedGoalSet.Kind.CONVERSATIONAL);
        assertThat(result.orElseThrow().getMessageSource())
                .isEqualTo(ResolvedGoalSet.MessageSource.SERVER_FIXED);
    }

    @Test
    void doesNotParseRecommendationQuantityConstraintComparisonOrReferenceLanguage() {
        assertThat(fastPath.tryResolve(ask("推荐两个后端项目"))).isEmpty();
        assertThat(fastPath.tryResolve(ask("比较这两个项目"))).isEmpty();
        assertThat(fastPath.tryResolve(ask("这个项目如何实现"))).isEmpty();
        assertThat(fastPath.tryResolve(ask("不要前端项目"))).isEmpty();
    }

    @Test
    void doesNotResolvePresets() {
        AgentTurnCommand.Ask preset = new AgentTurnCommand.Ask(
                UUID.randomUUID(),
                new AgentTurnCommand.Preset(
                        "question-sql-audit", "pcv1-0123456789abcdef"),
                AgentTurnCommand.SurfaceContext.empty(),
                ConversationWindow.empty());

        assertThat(fastPath.tryResolve(preset)).isEmpty();
    }

    private AgentTurnCommand.Ask ask(String text) {
        return new AgentTurnCommand.Ask(
                UUID.randomUUID(),
                new AgentTurnCommand.FreeText(text),
                AgentTurnCommand.SurfaceContext.empty(),
                ConversationWindow.empty());
    }
}
