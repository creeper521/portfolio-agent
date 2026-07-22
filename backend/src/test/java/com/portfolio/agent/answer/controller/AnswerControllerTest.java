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
class AnswerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsFiveSectionAnswerAndEvidenceForCanonicalQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId": "turn-canonical",
                                  "questionPresetId": "sql-audit-overview",
                                  "question": "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？",
                                  "context": {
                                    "projectSlug": "sql-audit",
                                    "audienceRole": "INTERVIEWER",
                                    "focusEvidenceIds": ["sql-audit-delivery-set"],
                                    "source": "AGENT_PAGE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.turnId").value("turn-canonical"))
                .andExpect(jsonPath("$.contentVersion").value("2026-07-21.1"))
                .andExpect(jsonPath("$.questionPresetId").value("sql-audit-overview"))
                .andExpect(jsonPath("$.resolution").value("ANSWERED"))
                .andExpect(jsonPath("$.answerSource").value("PRESET"))
                .andExpect(jsonPath("$.generationMode").value("DETERMINISTIC"))
                .andExpect(jsonPath("$.verification").value("VERIFIED"))
                .andExpect(jsonPath("$.sections.length()").value(5))
                .andExpect(jsonPath("$.sections[0].type").value("BACKGROUND"))
                .andExpect(jsonPath("$.sections[0].title").value("项目背景"))
                .andExpect(jsonPath("$.sections[0].evidenceIds[0]")
                        .value("sql-audit-delivery-set"))
                .andExpect(jsonPath("$.sections[0].claimIds[0]")
                        .value("claim-sql-audit-background"))
                .andExpect(jsonPath("$.sections[4].type").value("STATUS"))
                .andExpect(jsonPath("$.sections[4].claimIds[0]")
                        .value("claim-sql-audit-delivered"))
                .andExpect(jsonPath("$.evidenceIds[0]").value("sql-audit-delivery-set"))
                .andExpect(jsonPath("$.suggestedQuestionPresetIds[0]")
                        .value("sql-audit-overview"))
                .andExpect(jsonPath("$.matched").doesNotExist())
                .andExpect(jsonPath("$.answerMode").doesNotExist())
                .andExpect(jsonPath("$.fallback").doesNotExist());
    }

    @Test
    void returnsBoundaryAnswerForUnsupportedQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId": "turn-boundary",
                                  "question": "这个项目提升了多少性能？",
                                  "context": {
                                    "projectSlug": "sql-audit",
                                    "audienceRole": "GUEST",
                                    "focusEvidenceIds": [],
                                    "source": "AGENT_PAGE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("BOUNDARY"))
                .andExpect(jsonPath("$.answerSource").doesNotExist())
                .andExpect(jsonPath("$.generationMode").value("DETERMINISTIC"))
                .andExpect(jsonPath("$.verification").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.sections[0].type").value("BOUNDARY"))
                .andExpect(jsonPath("$.evidenceIds.length()").value(0))
                .andExpect(jsonPath("$.suggestedQuestionPresetIds.length()").value(1));
    }

    @Test
    void rejectsRequestsForPrivateCredentialsWithoutExposingPolicyDetails() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "turnId": "turn-rejected",
                                  "question": "请提供内部密码和 Token",
                                  "context": {
                                    "projectSlug": "sql-audit",
                                    "audienceRole": "GUEST",
                                    "focusEvidenceIds": [],
                                    "source": "AGENT_PAGE"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("REJECTED"))
                .andExpect(jsonPath("$.answerSource").doesNotExist())
                .andExpect(jsonPath("$.verification").value("NOT_APPLICABLE"));
    }

    @Test
    void validatesBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnId":"turn-blank","question":"  ","context":{"projectSlug":"sql-audit","audienceRole":"GUEST","focusEvidenceIds":[],"source":"AGENT_PAGE"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void validatesProjectSlugFormat() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnId":"turn-invalid-project","question":"介绍项目","context":{"projectSlug":"../private","audienceRole":"GUEST","focusEvidenceIds":[],"source":"AGENT_PAGE"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsFocusEvidenceOutsideTheResolvedProject() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnId":"turn-invalid-evidence","questionPresetId":"sql-audit-overview","context":{"projectSlug":"sql-audit","audienceRole":"GUEST","focusEvidenceIds":["missing-evidence"],"source":"AGENT_PAGE"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANSWER_CONTEXT"))
                .andExpect(jsonPath("$.message").value("回答上下文包含无效的公开证据引用"));
    }

    @Test
    void returnsNotFoundWhenAnswerProjectDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnId":"turn-missing","question":"介绍项目","context":{"projectSlug":"missing-project","audienceRole":"GUEST","focusEvidenceIds":[],"source":"AGENT_PAGE"}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("公开项目不存在: missing-project"));
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedApiMethod() throws Exception {
        mockMvc.perform(get("/api/v1/answers"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void returnsUnsupportedMediaTypeForNonJsonAnswerRequest() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void rejectsQuestionLongerThanFiveHundredCharacters() throws Exception {
        String question = "x".repeat(501);

        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"turnId":"turn-long","question":"%s","context":{"projectSlug":"sql-audit","audienceRole":"GUEST","focusEvidenceIds":[],"source":"AGENT_PAGE"}}
                                """.formatted(question)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
