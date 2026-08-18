package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.service.AnswerAdmissionGate;
import com.portfolio.agent.answer.service.AnswerIdempotencyCoordinator;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.RequestContext;
import com.portfolio.agent.common.web.RequestContextHolder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigrationProductionTurnServiceTest {

    @Test
    void executesClosedCommandThroughAdmissionAndIdempotencyBoundary() throws Exception {
        MigrationAgentTurnRuntime runtime = mock(MigrationAgentTurnRuntime.class);
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("你好"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
        when(runtime.answer(command)).thenReturn(new ConversationAnswerResult(
                command.getRequestId().toString(), "2026-08-05.1",
                ConversationIntent.CONVERSATION, ConversationAnswerScope.CONVERSATION,
                AnswerResolution.ANSWERED, "你好", List.of(), List.of(), false));
        MigrationProductionTurnService service = new MigrationProductionTurnService(
                runtime, new AnonymousSourceHasher(),
                new AnswerAdmissionGate(Clock.systemUTC(), 10, 2),
                new AnswerIdempotencyCoordinator<>(Clock.systemUTC(), Duration.ofMinutes(2)),
                Executors.newVirtualThreadPerTaskExecutor(), Duration.ofSeconds(2),
                Optional.empty(), Optional.empty());

        ConversationAnswerResult result = RequestContextHolder.callWith(
                RequestContext.create(null, command.getRequestId().toString()),
                () -> service.answer(command, "127.0.0.1"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
    }
}
