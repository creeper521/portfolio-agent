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

    @Test void clearIsBearerAuthorizedAndIdempotentAtTheResourceBoundary() throws Exception {
        AgentTurnLifecycleService lifecycle = mock(AgentTurnLifecycleService.class);
        when(lifecycle.clearConversation("token")).thenReturn(true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentConversationController(lifecycle)).build();
        mvc.perform(delete("/api/agent/conversations/current").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
