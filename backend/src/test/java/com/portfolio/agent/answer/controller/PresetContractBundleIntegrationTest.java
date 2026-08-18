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
                "portfolio.conversational-agent.enabled=false",
                "portfolio.answer-production.requests-per-minute=100"
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
                .andExpect(jsonPath("$.turnId").isString())
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
    void everyPublishedPresetRemainsPortfolioGroundedAndExecutable() throws Exception {
        String content = mockMvc.perform(get("/api/v1/public-content"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode presets = new ObjectMapper().readTree(content).path("questionPresets");

        int index = 0;
        for (JsonNode preset : presets) {
            String projectSlug = preset.path("projectSlug").isTextual()
                    ? preset.path("projectSlug").asText() : null;
            String caseSlug = preset.path("caseSlugs").isArray()
                    && !preset.path("caseSlugs").isEmpty()
                    ? preset.path("caseSlugs").get(0).asText() : null;
            String request = presetRequestForSubject(
                    "published-preset-" + index,
                    preset.path("id").asText(), preset.path("contractVersion").asText(),
                    projectSlug != null ? "PROJECT" : "CASE",
                    projectSlug != null ? projectSlug : caseSlug);

            mockMvc.perform(post("/api/v2/answers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                    .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                    .andExpect(jsonPath("$.resolution").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is("ANSWERED"),
                            org.hamcrest.Matchers.is("PARTIALLY_ANSWERED"))))
                    .andExpect(jsonPath("$.evidenceState").value("VERIFIED"))
                    .andExpect(jsonPath("$.blocks").isNotEmpty())
                    .andExpect(jsonPath("$.blocks[0].blockId").isNotEmpty())
                    .andExpect(jsonPath("$.blocks[0].sectionType").isNotEmpty())
                    .andExpect(jsonPath("$.blocks[0].title").isNotEmpty())
                    .andExpect(jsonPath("$.blocks[0].sourceReferences").isNotEmpty())
                    .andExpect(jsonPath("$.blocks[0].support.kind")
                            .value("VERIFIED_PUBLIC_EVIDENCE"))
                    .andExpect(jsonPath("$.publicSourceCatalog").isNotEmpty());
            index++;
        }
    }

    @Test
    void explicitlyNamedPublishedProjectsReachPortfolioComparison() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "compare-published-projects",
                                "比较 SQL 审计与故障排查工具和周末登录奖励 ABTest 完整闭环",
                                "\"context\":{\"audienceRole\":\"INTERVIEWER\","
                                        + "\"source\":\"AGENT_PAGE\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("ANSWERED"),
                        org.hamcrest.Matchers.is("PARTIALLY_ANSWERED"))))
                .andExpect(jsonPath("$.agentTurn").doesNotExist())
                .andExpect(jsonPath("$.blocks").isNotEmpty());
    }

    @Test
    void reusedRequestTokenWithDifferentQuestionReturnsConflictWithoutContextStore() throws Exception {
        String token = "6b2d8895-4108-4b4d-aee0-21f6e7c4f333";
        String first = """
                {"requestId":"%s","command":{"kind":"ASK","input":{"kind":"FREE_TEXT",
                 "text":"SQL 审计与故障排查工具"}},"conversationWindow":[]}
                """.formatted(token);
        String conflicting = """
                {"requestId":"%s","command":{"kind":"ASK","input":{"kind":"FREE_TEXT",
                 "text":"活动系统工程实践"}},"conversationWindow":[]}
                """.formatted(token);

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflicting))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownSemanticTurnContractReturnsPublicConflict() throws Exception {
        mockMvc.perform(post("/api/v2/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "unsupported-contract",
                                "解释乐观锁",
                                "\"agentTurnContract\":\"stp-v9\"")))
                .andExpect(status().isBadRequest());
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
                .andExpect(jsonPath("$.turnId").isString())
                .andExpect(jsonPath("$.intent").value("PORTFOLIO_GROUNDED"))
                .andExpect(jsonPath("$.answerScope").value("PORTFOLIO"))
                .andExpect(jsonPath("$.resolution").value("NOT_SUPPORTED"))
                .andExpect(jsonPath("$.constructionMode").value("EVIDENCE_COMPOSITION"))
                .andExpect(jsonPath("$.intentSource").value("RULE"))
                .andExpect(jsonPath("$.evidenceState").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.blocks").isEmpty())
                .andExpect(jsonPath("$.agentTurn").doesNotExist())
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
        String requestId = java.util.UUID.nameUUIDFromBytes(
                turnId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return """
                {"requestId":"%s","command":{"kind":"ASK","input":{"kind":"PRESET",
                 "presetId":"%s","presetRevision":"%s"}},
                 "surfaceContext":{"subjectHint":{"kind":"PROJECT","slug":"%s"},
                 "audienceRole":"INTERVIEWER","requestSource":"%s"},"conversationWindow":[]}
                """.formatted(requestId, presetId, contractVersion, projectSlug, source);
    }

    private String presetRequestForSubject(
            String turnId, String presetId, String revision, String subjectKind, String subjectSlug) {
        String requestId = java.util.UUID.nameUUIDFromBytes(
                turnId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return """
                {"requestId":"%s","command":{"kind":"ASK","input":{"kind":"PRESET",
                 "presetId":"%s","presetRevision":"%s"}},
                 "surfaceContext":{"subjectHint":{"kind":"%s","slug":"%s"},
                 "audienceRole":"INTERVIEWER","requestSource":"AGENT_PAGE"},
                 "conversationWindow":[]}
                """.formatted(requestId, presetId, revision, subjectKind, subjectSlug);
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
        String subject = "";
        String rejectedLegacyField = identityJson.contains("agentTurnContract")
                ? "\"agentTurnContract\":\"stp-v9\"," : "";
        java.util.regex.Matcher project = java.util.regex.Pattern
                .compile("\\\"projectSlug\\\":\\\"([^\\\"]+)\\\"").matcher(identityJson);
        java.util.regex.Matcher caseMatcher = java.util.regex.Pattern
                .compile("\\\"caseSlug\\\":\\\"([^\\\"]+)\\\"").matcher(identityJson);
        if (project.find()) {
            subject = "\"surfaceContext\":{\"subjectHint\":{\"kind\":\"PROJECT\",\"slug\":\""
                    + project.group(1) + "\"},\"audienceRole\":\"INTERVIEWER\","
                    + "\"requestSource\":\"AGENT_PAGE\"},";
        } else if (caseMatcher.find()) {
            subject = "\"surfaceContext\":{\"subjectHint\":{\"kind\":\"CASE\",\"slug\":\""
                    + caseMatcher.group(1) + "\"},\"audienceRole\":\"INTERVIEWER\","
                    + "\"requestSource\":\"AGENT_PAGE\"},";
        }
        return """
                {
                  "requestId": "%s",
                  "command":{"kind":"ASK","input":{"kind":"FREE_TEXT","text":"%s"}},
                  %s%s
                  "conversationWindow":[]
                }
                """.formatted(requestToken, question, rejectedLegacyField, subject);
    }
}
