package com.portfolio.agent.turn.api;

import com.portfolio.agent.turn.api.request.AgentTurnRequestMapper;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.turn.lifecycle.ActiveTurnCapacity;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTurnControllerContractTest {
    @Test void postUsesUnversionedResourceAndRootPublicTurnWithConversationEnvelope() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        UUID requestId = UUID.randomUUID();
        when(lifecycle.execute(any(), any())).thenReturn(new AgentTurnLifecycleService.Result(
                AgentTurnLifecycleService.Status.COMPLETED,
                new PublicAgentTurn.Conversational(requestId, "你好", List.of()),
                0, false, new AgentTurnLifecycleService.ConversationMetadata(
                "conversation-1", "resume-token-1", 4, null)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                controller(lifecycle)).build();
        mvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                                 "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}}}
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.kind").value("CONVERSATIONAL"))
                .andExpect(jsonPath("$.conversation.conversationId").value("conversation-1"))
                .andExpect(jsonPath("$.conversation.resumeToken").value("resume-token-1"))
                .andExpect(jsonPath("$.conversation.discussionRevision").value(4))
                .andExpect(jsonPath("$.conversation.activeDiscussion").doesNotExist())
                .andExpect(jsonPath("$.turn").doesNotExist());
    }

    @Test void inProgressUses409ErrorEnvelopeAndRetryAfter() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        UUID requestId = UUID.randomUUID();
        when(lifecycle.execute(any(), any())).thenReturn(new AgentTurnLifecycleService.Result(
                AgentTurnLifecycleService.Status.IN_PROGRESS, null, 4, false, null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                controller(lifecycle)).build();
        mvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                                 "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}}}
                                """.formatted(requestId)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "4"))
                .andExpect(jsonPath("$.error.code").value("TURN_IN_PROGRESS"));
    }

    @Test void settlementFailureNeverPublishesUnpersistedCapabilityTurn() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        UUID requestId = UUID.randomUUID();
        PublicAgentTurn unpersistedTurn = new PublicAgentTurn.CapabilityUnavailable(
                requestId,
                "SELECTED_MODEL_TEMPORARILY_UNAVAILABLE",
                "所选模型暂时不可用。",
                true,
                3L,
                List.of());
        when(lifecycle.execute(any(), any())).thenReturn(new AgentTurnLifecycleService.Result(
                AgentTurnLifecycleService.Status.COMPLETED,
                unpersistedTurn,
                0,
                true,
                new AgentTurnLifecycleService.ConversationMetadata(
                        "conversation-1", "resume-token-1", 4, null)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                controller(lifecycle)).build();

        mvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                                 "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}}}
                                """.formatted(requestId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.error.code").value("AGENT_STATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(jsonPath("$.kind").doesNotExist())
                .andExpect(jsonPath("$.conversation").doesNotExist());
    }

    @Test void settledReplayStillPublishesPublicTurn() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        UUID requestId = UUID.randomUUID();
        when(lifecycle.execute(any(), any())).thenReturn(new AgentTurnLifecycleService.Result(
                AgentTurnLifecycleService.Status.REPLAY,
                new PublicAgentTurn.Conversational(requestId, "已完成", List.of()),
                0,
                false,
                null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                controller(lifecycle)).build();

        mvc.perform(post("/api/agent/turns").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"%s","modelSelection":{"kind":"NONE"},
                                 "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"你好"}}}
                                """.formatted(requestId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.kind").value("CONVERSATIONAL"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test void deleteMapsCancelWinnerTo204() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        UUID requestId = UUID.randomUUID();
        when(lifecycle.cancel(null, requestId))
                .thenReturn(AgentTurnLifecycleService.CancelStatus.CANCELLED);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                controller(lifecycle)).build();
        mvc.perform(delete("/api/agent/turns/{requestId}", requestId))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private AgentTurnController controller(AgentTurnLifecycleService lifecycle) {
        return new AgentTurnController(
                lifecycle,
                new AgentTurnRequestMapper(),
                new ClientAddressResolver(false, Set.of()),
                new AnonymousSourceHasher(new byte[32]),
                new AgentRequestAdmissionGate(
                        Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC),
                        100, 10, 100),
                new ActiveTurnCapacity(10));
    }
}
