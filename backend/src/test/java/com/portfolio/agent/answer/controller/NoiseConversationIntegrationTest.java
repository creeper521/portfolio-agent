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
                "portfolio.model-expression.enabled=false",
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
                                  "turnId": "turn-noise-112233",
                                  "requestToken": "b9ff1349-1ea3-4ed1-b46d-31115704b20b",
                                  "question": "112233",
                                  "messages": [],
                                  "context": { "audienceRole": "INTERVIEWER", "source": "AGENT_PAGE" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("NEEDS_CLARIFICATION"))
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
                                  "turnId": "turn-noise-active-project",
                                  "requestToken": "0ed673a7-7734-4f39-8a0c-9871f550cfa5",
                                  "question": "1",
                                  "messages": [],
                                  "context": { "audienceRole": "INTERVIEWER", "source": "AGENT_PAGE" },
                                  "semanticContext": {
                                    "activeSubjects": [
                                      { "subjectType": "PROJECT", "subjectId": "sql-audit-delivery-set" }
                                    ],
                                    "audienceRole": "INTERVIEWER",
                                    "requestSource": "AGENT_PAGE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.evidenceState").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.blocks").isEmpty())
                .andExpect(jsonPath("$.publicSourceCatalog").doesNotExist());
    }
}
