package com.portfolio.agent.portfolio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.PortfolioAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortfolioAgentApplication.class)
@AutoConfigureMockMvc
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsAtomicPortfolioSnapshotWithFrozenTopLevelContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.contentVersion").value("2026-08-05.1"))
                .andExpect(jsonPath("$.runtimeBundleHash").value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andExpect(jsonPath("$.questionPresets.length()").value(18))
                .andExpect(jsonPath("$.questionPresets[?(@.id=='question-public-assets-overview')]").isEmpty())
                .andExpect(jsonPath("$.projects[0].code").value("P-01"))
                .andExpect(jsonPath("$.projects[0].evidenceIds[0]")
                        .value("sql-audit-delivery-set"))
                .andExpect(jsonPath("$.projects[0].evidenceIds.length()").value(4))
                .andExpect(jsonPath("$.projects[0].suggestedQuestions.length()").value(6))
                .andExpect(jsonPath("$.evidence[0].code").value("E-01"))
                .andExpect(jsonPath("$.evidence[0].publicStatus").value("APPROVED"))
                .andExpect(jsonPath("$.evidence[0].projectSlugs[0]").value("sql-audit"))
                .andExpect(jsonPath("$.evidence[0].claimIds.length()").value(5))
                .andExpect(jsonPath("$.evidence[0].claimIds[0]")
                        .value("claim-sql-audit-background"))
                .andExpect(jsonPath("$.evidence[0].claimIds[4]")
                        .value("claim-sql-audit-delivered"))
                .andExpect(jsonPath("$.evidence[0].supportedClaims").doesNotExist())
                .andExpect(jsonPath("$.evidence.length()").value(63))
                .andExpect(jsonPath("$.claims.length()").value(88))
                .andExpect(jsonPath("$.claimEvidenceLinks.length()").value(88))
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
                        .value("timeline-sql-audit-delivery"))
                .andExpect(jsonPath("$.timeline[0].caseSlugs").isEmpty())
                .andExpect(jsonPath("$.timeline.length()").value(12))
                .andExpect(jsonPath("$.projects.length()").value(6))
                .andExpect(jsonPath("$.projects[?(@.slug=='weekend-login-abtest')].contributionType")
                        .value("PRIMARY"))
                .andExpect(jsonPath("$.projects[?(@.slug=='weekend-login-abtest')].caseCount")
                        .value(3))
                .andExpect(jsonPath("$.projects[?(@.slug=='weekend-login-abtest')].featuredCases.length()")
                        .value(3))
                .andExpect(jsonPath("$.cases.length()").value(52))
                .andExpect(jsonPath("$.caseSlugsByEvidenceId").isMap())
                .andExpect(jsonPath("$.caseSlugsByEvidenceId['evidence-case-role-reset-guide-and-acceptance'][0]")
                        .value("test-role-reset"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Set<String> names = new LinkedHashSet<>();
        root.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(
                "contentVersion",
                "runtimeBundleHash",
                "publishedAt",
                "owner",
                "collections",
                "projects",
                "cases",
                "claims",
                "claimEvidenceLinks",
                "evidence",
                "timeline",
                "caseSlugsByEvidenceId",
                "questionPresets",
                "agentAvailability");
    }
}
