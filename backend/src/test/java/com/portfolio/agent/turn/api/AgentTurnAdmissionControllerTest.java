package com.portfolio.agent.turn.api;

import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.GlobalExceptionHandler;
import com.portfolio.agent.turn.api.request.AgentTurnRequestMapper;
import com.portfolio.agent.turn.lifecycle.ActiveTurnCapacity;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTurnAdmissionControllerTest {

    @Test
    void rateLimitUsesOneDerivedRetryDelayInHeaderAndEnvelope() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AnonymousSourceHasher hasher = new AnonymousSourceHasher(new byte[32]);
        AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(clock, 1, 2, 100);
        gate.acquire(hasher.hash("198.51.100.9"), UUID.randomUUID()).close();
        clock.advance(Duration.ofSeconds(55));
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        MockMvc mvc = mvc(lifecycle, hasher, gate, new ActiveTurnCapacity(8));
        UUID requestId = UUID.randomUUID();

        mvc.perform(post("/api/agent/turns")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeText(requestId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(jsonPath("$.error.retryAfterSeconds").value(5));

        verify(lifecycle, never()).execute(any(), any());
    }

    @Test
    void lifecycleFailureReleasesSourceAndGlobalLeases() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        AnonymousSourceHasher hasher = new AnonymousSourceHasher(new byte[32]);
        AgentRequestAdmissionGate gate = new AgentRequestAdmissionGate(clock, 10, 1, 100);
        ActiveTurnCapacity capacity = new ActiveTurnCapacity(1);
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        UUID completedRequestId = UUID.randomUUID();
        when(lifecycle.execute(any(), any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(completed(completedRequestId));
        MockMvc mvc = mvc(lifecycle, hasher, gate, capacity);

        mvc.perform(post("/api/agent/turns")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeText(UUID.randomUUID())))
                .andExpect(status().isInternalServerError());

        mvc.perform(post("/api/agent/turns")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freeText(completedRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CONVERSATIONAL"));
    }

    private MockMvc mvc(
            AgentTurnLifecycleService lifecycle,
            AnonymousSourceHasher hasher,
            AgentRequestAdmissionGate gate,
            ActiveTurnCapacity capacity) {
        AgentTurnController controller = new AgentTurnController(
                lifecycle,
                new AgentTurnRequestMapper(),
                new ClientAddressResolver(false, Set.of()),
                hasher,
                gate,
                capacity);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AgentTurnLifecycleService.Result completed(UUID requestId) {
        return new AgentTurnLifecycleService.Result(
                AgentTurnLifecycleService.Status.COMPLETED,
                new PublicAgentTurn.Conversational(requestId, "你好", List.of()),
                0,
                false,
                new AgentTurnLifecycleService.ConversationMetadata(
                        "conversation-1", "resume-token-1"));
    }

    private String freeText(UUID requestId) {
        return """
                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                 "command":{"kind":"ASK","input":{
                 "kind":"FREE_TEXT","text":"你好"}},"conversationWindow":[]}
                """.formatted(requestId);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
