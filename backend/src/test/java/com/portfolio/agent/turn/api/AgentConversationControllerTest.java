package com.portfolio.agent.turn.api;

import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentConversationControllerTest {
    @Test void currentRequiresBearerAndReturnsOnlySafeSummary() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        when(lifecycle.currentConversation("token"))
                .thenReturn(new AgentTurnLifecycleService.ConversationStatus(true, "conversation-1"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentConversationController(lifecycle)).build();
        mvc.perform(get("/api/agent/conversations/current").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.conversationId").value("conversation-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.messages").doesNotExist())
                .andExpect(jsonPath("$.handles").doesNotExist());
        mvc.perform(get("/api/agent/conversations/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentProjectsActiveDiscussionWithBackendOwnedActions() throws Exception {
        AgentTurnLifecycleService lifecycle =
                mock(AgentTurnLifecycleService.class);
        when(lifecycle.currentConversation("token"))
                .thenReturn(new AgentTurnLifecycleService.ConversationStatus(
                        true, "conversation-1",
                        new AgentTurnLifecycleService.DiscussionSummary(
                                com.portfolio.agent.turn.continuation.ActiveDiscussionPointer.Status.ACTIVE,
                                "project-a", "项目 A", "/projects/project-a",
                                java.time.Instant.parse(
                                        "2026-08-20T08:30:00Z"),
                                "discussion_handle_123")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AgentConversationController(lifecycle)).build();

        mvc.perform(get("/api/agent/conversations/current")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDiscussion.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.activeDiscussion.subject.reference")
                        .value("project-a"))
                .andExpect(jsonPath("$.activeDiscussion.routeContinuation.operation")
                        .value("ROUTE_IN_CONTEXT"))
                .andExpect(jsonPath("$.activeDiscussion.exitAction.continuation.operation")
                        .value("EXIT_CONTEXT"))
                .andExpect(jsonPath("$.activeDiscussion.reenterAction")
                        .doesNotExist())
                .andExpect(jsonPath("$.activeDiscussion.contextHandle")
                        .doesNotExist());
    }

    @Test void clearIsBearerAuthorizedAndIdempotentAtTheResourceBoundary() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        when(lifecycle.clearConversation("token")).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentConversationController(lifecycle)).build();
        mvc.perform(delete("/api/agent/conversations/current").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
