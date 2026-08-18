package com.portfolio.agent.answer.controller;

import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PortfolioAgentApplication.class,
        properties = {
                "portfolio.conversational-agent.enabled=false"
        }
)
@AutoConfigureMockMvc
class NoiseConversationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meaninglessNumericInputRequiresClarificationWithoutPublicEvidence() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "b9ff1349-1ea3-4ed1-b46d-31115704b20b",
                                  "command": {"kind":"ASK","input":{"kind":"FREE_TEXT","text":"112233"}},
                                  "conversationWindow": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.evidenceState").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.blocks").isEmpty())
                .andExpect(jsonPath("$.publicSourceCatalog").doesNotExist());
    }

    @Test
    void unformedInputDoesNotInheritAnActiveProjectSubject() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "0ed673a7-7734-4f39-8a0c-9871f550cfa5",
                                  "command": {"kind":"ASK","input":{"kind":"FREE_TEXT","text":"1"}},
                                  "surfaceContext": {"subjectHint":{"kind":"PROJECT","slug":"sql-audit"},
                                    "audienceRole":"INTERVIEWER","requestSource":"AGENT_PAGE"},
                                  "conversationWindow": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.evidenceState").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.blocks").isEmpty())
                .andExpect(jsonPath("$.publicSourceCatalog").doesNotExist());
    }
}
