package com.portfolio.agent.answer.controller;

import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@AutoConfigureMockMvc
class CaseConversationBundleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesPublishedCaseAcceptsCaseConversationAndFailsClosedForUnknownCase()
            throws Exception {
        mockMvc.perform(get("/api/v1/cases/multilingual-image-preservation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("multilingual-image-preservation"));

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("turn-known-case", "multilingual-image-preservation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnId").value("turn-known-case"))
                .andExpect(jsonPath("$.intent").value("GENERAL_KNOWLEDGE"));

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("turn-unknown-case", "unknown-case")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("BOUNDARY"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].sourceScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.blocks[0].claimIds.length()").value(0))
                .andExpect(jsonPath("$.blocks[0].evidenceIds.length()").value(0));
    }

    private String request(String turnId, String caseSlug) {
        return """
                {
                  "turnId": "%s",
                  "question": "How was this case verified?",
                  "messages": [],
                  "context": {
                    "caseSlug": "%s",
                    "audienceRole": "INTERVIEWER",
                    "source": "CASE"
                  }
                }
                """.formatted(turnId, caseSlug);
    }
}
