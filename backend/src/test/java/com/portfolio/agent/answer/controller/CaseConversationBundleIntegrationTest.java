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

@SpringBootTest(
        classes = PortfolioAgentApplication.class,
        properties = {
                "portfolio.model-expression.enabled=false",
                "portfolio.conversational-agent.enabled=false"
        }
)
@AutoConfigureMockMvc
class CaseConversationBundleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesPublishedCaseAcceptsCaseConversationAndFailsClosedForUnknownCase()
            throws Exception {
        mockMvc.perform(get("/api/v1/cases/multilingual-image-preservation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("multilingual-image-preservation"))
                .andExpect(jsonPath("$.projectSlug").value("image-upload-audit"))
                .andExpect(jsonPath("$.evidence.length()").value(1))
                .andExpect(jsonPath("$.evidence[0].id")
                        .value("evidence-case-multilingual-implementation-and-regression"))
                .andExpect(jsonPath("$.suggestedQuestions.length()").value(3))
                .andExpect(jsonPath("$.suggestedQuestions[0]")
                        .value("多语言图片分次上传为什么会覆盖既有结果，最终如何修复并验证？"));

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("turn-known-case", "multilingual-image-preservation",
                                "这个案例如何验证？")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnId").value("turn-known-case"))
                .andExpect(jsonPath("$.contentVersion").value("2026-08-05.1"))
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("ANSWERED"))
                .andExpect(jsonPath("$.constructionMode")
                        .value("EVIDENCE_COMPOSITION"))
                .andExpect(jsonPath("$.intentSource").value("RULE"))
                .andExpect(jsonPath("$.evidenceState").value("VERIFIED"))
                .andExpect(jsonPath("$.blocks").isNotEmpty())
                .andExpect(jsonPath("$.degraded").value(false));

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("turn-unknown-case", "unknown-case",
                                "这个案例如何验证？")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.noticeCode").value("STRUCTURED_SUBJECT_INVALID"))
                .andExpect(jsonPath("$.evidenceState").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.blocks.length()").value(1))
                .andExpect(jsonPath("$.blocks[0].sourceScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.blocks[0].claimIds.length()").value(0))
                .andExpect(jsonPath("$.blocks[0].evidenceIds.length()").value(0));

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("turn-unknown-rule", "unknown-case",
                                "这个案例怎么实现？")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.noticeCode").value("STRUCTURED_SUBJECT_INVALID"))
                .andExpect(jsonPath("$.intentSource").value("RULE"))
                .andExpect(jsonPath("$.evidenceState").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.degraded").value(false));
    }

    private String request(String turnId, String caseSlug, String question) {
        String requestToken = java.util.UUID.nameUUIDFromBytes(
                turnId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return """
                {
                  "turnId": "%s",
                  "requestToken": "%s",
                  "question": "%s",
                  "messages": [],
                  "context": {
                    "caseSlug": "%s",
                    "audienceRole": "INTERVIEWER",
                    "source": "CASE"
                  }
                }
                """.formatted(turnId, requestToken, question, caseSlug);
    }
}
