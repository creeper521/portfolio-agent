package com.portfolio.agent.portfolio.controller;

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

    @Test
    void returnsCompleteReviewedPublicContent() throws Exception {
        mockMvc.perform(get("/api/v1/public-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersion").value("2026-07-21.1"))
                .andExpect(jsonPath("$.runtimeBundleHash").value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andExpect(jsonPath("$.questionPresets.length()").value(1))
                .andExpect(jsonPath("$.questionPresets[0].id").value("sql-audit-overview"))
                .andExpect(jsonPath("$.questionPresets[0].projectSlug").value("sql-audit"))
                .andExpect(jsonPath("$.questionPresets[0].audiences.length()").value(4))
                .andExpect(jsonPath("$.questionPresets[0].placements[0]").value("HOME"))
                .andExpect(jsonPath("$.projects[0].code").value("P-01"))
                .andExpect(jsonPath("$.projects[0].evidenceIds[0]")
                        .value("sql-audit-delivery-set"))
                .andExpect(jsonPath("$.projects[0].suggestedQuestions.length()").value(1))
                .andExpect(jsonPath("$.evidence[0].code").value("E-01"))
                .andExpect(jsonPath("$.evidence[0].publicStatus").value("APPROVED"))
                .andExpect(jsonPath("$.evidence[0].projectSlugs[0]").value("sql-audit"))
                .andExpect(jsonPath("$.evidence[0].claimIds.length()").value(5))
                .andExpect(jsonPath("$.evidence[0].claimIds[0]")
                        .value("claim-sql-audit-background"))
                .andExpect(jsonPath("$.evidence[0].claimIds[4]")
                        .value("claim-sql-audit-delivered"))
                .andExpect(jsonPath("$.evidence[0].supportedClaims").doesNotExist())
                .andExpect(jsonPath("$.claims.length()").value(5))
                .andExpect(jsonPath("$.claims[4].id")
                        .value("claim-sql-audit-delivered"))
                .andExpect(jsonPath("$.claims[4].subjectType").value("PROJECT"))
                .andExpect(jsonPath("$.claims[4].category").value("OUTCOME"))
                .andExpect(jsonPath("$.claims[4].achievementStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.claims[4].verificationBasis")
                        .value("EVIDENCE_SUPPORTED"))
                .andExpect(jsonPath("$.claims[4].verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.claims[4].materiality").value("KEY"))
                .andExpect(jsonPath("$.claimEvidenceLinks[0].supportType").value("DIRECT"))
                .andExpect(jsonPath("$.timeline[0].id")
                        .value("timeline-sql-audit-delivery"));
    }
}
