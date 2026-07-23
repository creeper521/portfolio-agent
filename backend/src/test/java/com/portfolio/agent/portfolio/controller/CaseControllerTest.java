package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseType;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;
import com.portfolio.agent.portfolio.exception.CaseNotFoundException;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
@Import(PortfolioResponseMapper.class)
class CaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    private CaseDetails details;

    @BeforeEach
    void setUp() {
        details = new CaseDetails(
                multilingualCase(),
                List.of(caseEvidence()),
                List.of("多语言图片上传修复解决了什么问题？"),
                "sql-audit"
        );
    }

    @Test
    void returnsCaseSummariesWithoutInternalRelationIds() throws Exception {
        when(portfolioService.getCases()).thenReturn(List.of(details));

        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].length()").value(7))
                .andExpect(jsonPath("$[0].slug")
                        .value("multilingual-image-preservation"))
                .andExpect(jsonPath("$[0].code").value("CASE-01"))
                .andExpect(jsonPath("$[0].type").value("FEATURE"))
                .andExpect(jsonPath("$[0].achievementStatus").value("IMPLEMENTED_TESTED"))
                .andExpect(jsonPath("$[0].contributionType").value("PRIMARY"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].projectId").doesNotExist())
                .andExpect(jsonPath("$[0].claimIds").doesNotExist())
                .andExpect(jsonPath("$[0].evidenceIds").doesNotExist())
                .andExpect(jsonPath("$[0].timelineEventIds").doesNotExist())
                .andExpect(jsonPath("$[0].questionPresetIds").doesNotExist());
    }

    @Test
    void returnsCaseDetailWithResolvedProjectAndPublicEvidence() throws Exception {
        when(portfolioService.getCase("multilingual-image-preservation")).thenReturn(details);

        mockMvc.perform(get("/api/v1/cases/multilingual-image-preservation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$.verification[0]")
                        .value("先上传德语，再上传法语并查询"))
                .andExpect(jsonPath("$.projectSlug").value("sql-audit"))
                .andExpect(jsonPath("$.evidence[0].id")
                        .value("evidence-case-multilingual"))
                .andExpect(jsonPath("$.evidence[0].rawContent").doesNotExist())
                .andExpect(jsonPath("$.suggestedQuestions[0]")
                        .value("多语言图片上传修复解决了什么问题？"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.claimIds").doesNotExist())
                .andExpect(jsonPath("$.evidenceIds").doesNotExist())
                .andExpect(jsonPath("$.timelineEventIds").doesNotExist())
                .andExpect(jsonPath("$.questionPresetIds").doesNotExist());
    }

    @Test
    void returnsExplicitNullProjectSlugForStandaloneCase() throws Exception {
        CaseDetails standaloneDetails = new CaseDetails(
                standaloneCase(),
                List.of(),
                List.of("How was the standalone case verified?"),
                null
        );
        when(portfolioService.getCase("standalone-case")).thenReturn(standaloneDetails);

        mockMvc.perform(get("/api/v1/cases/standalone-case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$.projectSlug").value(nullValue()));
    }

    @Test
    void returnsCaseNotFoundErrorFromService() throws Exception {
        when(portfolioService.getCase("missing"))
                .thenThrow(new CaseNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/cases/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private static CaseStudy multilingualCase() {
        return new CaseStudy(
                "case-multilingual",
                "CASE-01",
                "multilingual-image-preservation",
                CaseType.FEATURE,
                "多语言图片上传保留",
                "连续上传不同语言图片后保留完整语言集合",
                "后一次语言上传覆盖先前可见语言集合",
                List.of("持久化语言集合取已有语言与本次上传语言的并集"),
                List.of("按实际上传结果合并语言集合"),
                List.of("先上传德语，再上传法语并查询"),
                "两种语言结果可以同时查询",
                List.of("不公开内部存储路径"),
                AchievementStatus.IMPLEMENTED_TESTED,
                ContributionType.PRIMARY,
                "project-1",
                List.of("claim-case-multilingual"),
                List.of("evidence-case-multilingual"),
                List.of("timeline-case-multilingual"),
                List.of("question-case-multilingual")
        );
    }

    private static EvidenceRecord caseEvidence() {
        return new EvidenceRecord(
                "evidence-case-multilingual",
                "E-CASE-01",
                "多语言图片实现与回归证据",
                EvidenceType.DOCUMENT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                2,
                "覆盖实现说明与回归验证",
                EvidenceStatus.APPROVED,
                false
        );
    }

    private static CaseStudy standaloneCase() {
        return new CaseStudy(
                "case-standalone",
                "CASE-02",
                "standalone-case",
                CaseType.FEATURE,
                "Standalone case",
                "A case without a project relation",
                "Standalone problem",
                List.of("Standalone action"),
                List.of("Standalone decision"),
                List.of("Standalone verification"),
                "Standalone outcome",
                List.of("Standalone limitation"),
                AchievementStatus.IMPLEMENTED_TESTED,
                ContributionType.PRIMARY,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
