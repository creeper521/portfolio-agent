package com.portfolio.agent.answer.controller;

import com.portfolio.agent.PortfolioAgentApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PresetContractBundleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sqlAuditOverviewPresetReplaysTheScreenshotRequestWithoutModelCapabilities()
            throws Exception {
        String content = mockMvc.perform(get("/api/v1/public-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionPresets.length()").value(18))
                .andReturn().getResponse().getContentAsString();
        JsonNode presets = new ObjectMapper().readTree(content).path("questionPresets");
        String contractVersion = null;
        for (JsonNode preset : presets) {
            if ("sql-audit-overview".equals(preset.path("id").asText())) {
                contractVersion = preset.path("contractVersion").asText();
                break;
            }
        }
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalStateException("sql-audit-overview must expose an active contract version");
        }
        final String activeContractVersion = contractVersion;

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presetRequest(
                                "preset-sql-audit",
                                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？",
                                "sql-audit-overview",
                                activeContractVersion,
                                "sql-audit",
                                "AGENT_PAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnId").value("preset-sql-audit"))
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("ANSWERED"))
                .andExpect(jsonPath("$.constructionMode").value("EVIDENCE_COMPOSITION"))
                .andExpect(jsonPath("$.intentSource").value("PRESET"))
                .andExpect(jsonPath("$.evidenceState").value("VERIFIED"))
                .andExpect(jsonPath("$.questionPresetId").value("sql-audit-overview"))
                .andExpect(jsonPath("$.contractVersion").value(activeContractVersion))
                .andExpect(jsonPath("$.blocks").isNotEmpty())
                .andExpect(jsonPath("$.degraded").value(false));
    }

    @Test
    void roleResetBackgroundSuggestionDoesNotBypassP3EvidenceSupport()
            throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suggestionRequest(
                                "structured-role-reset",
                                "测试角色重置工具的背景和目标是什么？",
                                "role-reset-tool",
                                "AGENT_PAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnId").value("structured-role-reset"))
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("NOT_SUPPORTED"))
                .andExpect(jsonPath("$.constructionMode").value("TEMPLATE"))
                .andExpect(jsonPath("$.intentSource").value("RULE"))
                .andExpect(jsonPath("$.evidenceState").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.blocks").isEmpty())
                .andExpect(jsonPath("$.agentTurn.outcome.taskSummary.items[0].reasonCodes[0]")
                        .value("PORTFOLIO_EVIDENCE_INSUFFICIENT"))
                .andExpect(jsonPath("$.degraded").value(false));
    }

    @Test
    void staleContractVersionFailsClosedWithoutSearchFallback() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presetRequest(
                                "preset-stale",
                                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？",
                                "sql-audit-overview",
                                "pcv1-0000000000000000",
                                "sql-audit",
                                "AGENT_PAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.noticeCode").value("PRESET_CONTRACT_STALE"))
                .andExpect(jsonPath("$.intentSource").value("PRESET"));
    }

    @Test
    void unknownPresetIdFailsClosedAsContractUnavailable() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(presetRequest(
                                "preset-unknown",
                                "请详细介绍 SQL 审计与故障排查工具项目",
                                "question-unknown-preset",
                                "pcv1-0000000000000000",
                                "sql-audit",
                                "AGENT_PAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.noticeCode").value("PRESET_CONTRACT_UNAVAILABLE"));
    }

    @Test
    void unknownStructuredSlugReturnsInvalidInputWithoutGeneralFallback() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(suggestionRequest(
                                "structured-unknown",
                                "这个项目如何实现？",
                                "unknown-project-slug",
                                "AGENT_PAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.noticeCode").value("STRUCTURED_SUBJECT_INVALID"))
                 .andExpect(jsonPath("$.blocks[0].claimIds").doesNotExist())
                 .andExpect(jsonPath("$.blocks[0].evidenceIds").doesNotExist());
    }

    private String presetRequest(
            String turnId,
            String question,
            String presetId,
            String contractVersion,
            String projectSlug,
            String source
    ) {
        return request(turnId, question,
                ("\"questionPresetId\":\"%s\",\"contractVersion\":\"%s\","
                        + "\"context\":{\"projectSlug\":\"%s\",\"audienceRole\":\"INTERVIEWER\","
                        + "\"source\":\"%s\"}")
                        .formatted(presetId, contractVersion, projectSlug, source));
    }

    private String suggestionRequest(
            String turnId,
            String question,
            String projectSlug,
            String source
    ) {
        return request(turnId, question,
                ("\"context\":{\"projectSlug\":\"%s\",\"audienceRole\":\"INTERVIEWER\","
                        + "\"source\":\"%s\"}")
                        .formatted(projectSlug, source));
    }

    private String request(String turnId, String question, String identityJson) {
        String requestToken = java.util.UUID.nameUUIDFromBytes(
                turnId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return """
                {
                  "turnId": "%s",
                  "requestToken": "%s",
                  "question": "%s",
                  "messages": [],
                  %s
                }
                """.formatted(turnId, requestToken, question, identityJson);
    }
}
