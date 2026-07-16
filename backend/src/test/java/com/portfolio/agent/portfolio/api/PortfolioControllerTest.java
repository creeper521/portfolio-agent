package com.portfolio.agent.portfolio.api;

import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@AutoConfigureMockMvc
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPortfolioHomeSummary() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.role").value("Java 后端开发实习生"))
                .andExpect(jsonPath("$.projects[0].slug").value("sql-audit"))
                .andExpect(jsonPath("$.projects[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.projects[0].contributionType").value("PRIMARY"));
    }

    @Test
    void returnsProjectDetailWithApprovedEvidenceAndSuggestion() throws Exception {
        mockMvc.perform(get("/api/v1/projects/sql-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("SQL 审计与故障排查工具"))
                .andExpect(jsonPath("$.responsibilities.length()").value(4))
                .andExpect(jsonPath("$.evidence[0].title").value("SQL 审计工具交付证据集"))
                .andExpect(jsonPath("$.evidence[0].publicStatus").value("APPROVED"))
                .andExpect(jsonPath("$.suggestedQuestions.length()").value(1));
    }

    @Test
    void returnsNotFoundForUnknownProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
