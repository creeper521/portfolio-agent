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
                                  "projectSlug": "sql-audit",
                                  "question": "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.answerMode").value("DETERMINISTIC"))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.answer.sections.length()").value(5))
                .andExpect(jsonPath("$.answer.sections[0].type").value("BACKGROUND"))
                .andExpect(jsonPath("$.answer.sections[4].type").value("STATUS"))
                .andExpect(jsonPath("$.evidence[0].title").value("SQL 审计工具交付证据集"));
    }

    @Test
    void returnsBoundaryAnswerForUnsupportedQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectSlug": "sql-audit",
                                  "question": "这个项目提升了多少性能？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.answer.sections[0].type").value("BOUNDARY"))
                .andExpect(jsonPath("$.evidence.length()").value(0))
                .andExpect(jsonPath("$.suggestedQuestions.length()").value(1));
    }

    @Test
    void validatesBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectSlug": "sql-audit", "question": "  "}
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
                                {"projectSlug": "../private", "question": "介绍项目"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsNotFoundWhenAnswerProjectDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectSlug": "missing-project", "question": "介绍项目"}
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
                                {"projectSlug":"sql-audit","question":"%s"}
                                """.formatted(question)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
