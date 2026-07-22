package com.portfolio.agent.portfolio.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSnapshotValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PortfolioSnapshotValidator validator = new PortfolioSnapshotValidator();

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertInvalid(validJson().replace("\"schemaVersion\": \"2.0\"", "\"schemaVersion\": \"3.0\""),
                "schemaVersion");
    }

    @Test
    void rejectsDanglingClaimEvidenceLink() {
        assertInvalid(validJson().replace(
                "\"evidenceId\": \"sql-audit-delivery-set\"",
                "\"evidenceId\": \"missing-evidence\""
        ), "claim evidence link reference");
    }

    @Test
    void rejectsAchievementClaimWithoutApprovedDirectLink() {
        assertInvalid(validJson().replace("\"supportType\": \"DIRECT\"",
                "\"supportType\": \"CONTEXTUAL\""), "DIRECT");
    }

    @Test
    void rejectsVerifiedClaimWithSelfDeclaredBasis() {
        assertInvalid(validJson().replace(
                "\"verificationBasis\": \"EVIDENCE_SUPPORTED\"",
                "\"verificationBasis\": \"SELF_DECLARED\""
        ), "verificationStatus");
    }

    @Test
    void rejectsDuplicateProjectIds() {
        String duplicateProject = projectJson().replace(
                "\"slug\": \"sql-audit\"", "\"slug\": \"sql-audit-copy\"");
        String json = validJson().replace(projectJson(), projectJson() + "," + duplicateProject);

        assertInvalid(json, "duplicate project id");
    }

    @Test
    void rejectsDuplicateProjectCodes() {
        String secondProject = projectJson()
                .replace("\"id\": \"sql-audit-project\"", "\"id\": \"copy-project\"")
                .replace("\"slug\": \"sql-audit\"", "\"slug\": \"sql-audit-copy\"");
        String secondQuestion = questionJson()
                .replace("\"id\": \"sql-audit-overview\"", "\"id\": \"copy-overview\"")
                .replace("\"projectIds\": [\"sql-audit-project\"]",
                        "\"projectIds\": [\"copy-project\"]");
        String json = validJson()
                .replace(projectJson(), projectJson() + "," + secondProject)
                .replace(questionJson(), questionJson() + "," + secondQuestion);

        assertInvalid(json, "duplicate project code");
    }

    @Test
    void rejectsDanglingQuestionReference() {
        assertInvalid(validJson().replace("\"projectIds\": [\"sql-audit-project\"]",
                "\"projectIds\": [\"missing-project\"]"), "question project reference");
    }

    @Test
    void rejectsEvidenceThatIsNotApproved() {
        assertInvalid(validJson().replace("\"publicStatus\": \"APPROVED\"",
                "\"publicStatus\": \"PENDING\""), "APPROVED");
    }

    @Test
    void rejectsEvidenceThatExposesRawContent() {
        assertInvalid(validJson().replace("\"rawContentPublic\": false",
                "\"rawContentPublic\": true"), "raw content");
    }

    @Test
    void rejectsEvidenceWithoutExplicitRawContentFlag() {
        assertInvalid(validJson().replace("\"rawContentPublic\": false",
                        "\"rawContentPublic\": null"),
                "rawContentPublic");
    }

    @Test
    void rejectsBlankEvidenceCode() {
        assertInvalid(validJson().replace("\"code\": \"E-01\"", "\"code\": \" \""),
                "evidence code");
    }

    @Test
    void rejectsBlankProjectContent() {
        assertInvalid(validJson().replace("\"title\": \"SQL 审计与故障排查工具\"",
                "\"title\": \"  \""), "project title");
    }

    @Test
    void rejectsProjectWithoutEvidenceAssociation() {
        String invalidProject = projectJson().replace(
                "\"evidenceIds\": [\"sql-audit-delivery-set\"]", "\"evidenceIds\": []");

        assertInvalid(validJson().replace(projectJson(), invalidProject), "project evidenceIds");
    }

    @Test
    void rejectsDuplicateTimelineIds() {
        String invalidProject = projectJson().replace(
                "\"evidenceIds\": [\"sql-audit-delivery-set\"]", "\"evidenceIds\": []");
        String json = validJson()
                .replace(projectJson(), invalidProject)
                .replace(timelineJson(), timelineJson() + "," + timelineJson());

        assertInvalid(json, "duplicate timeline id");
    }

    @Test
    void rejectsEvidenceWithReversedPeriod() {
        assertInvalid(validJson().replace("\"periodEnd\": \"2026-07-10\"",
                "\"periodEnd\": \"2026-05-01\""), "period");
    }

    @Test
    void rejectsQuestionWithoutText() {
        assertInvalid(validJson().replace("\"text\": \"请详细介绍 SQL 审计与故障排查工具项目\"",
                "\"text\": \"\""), "question text");
    }

    @Test
    void rejectsTimelineWithMissingProject() {
        String invalidTimeline = timelineJson().replace(
                "\"projectIds\": [\"sql-audit-project\"]",
                "\"projectIds\": [\"missing-project\"]");
        assertInvalid(validJson().replace(timelineJson(), invalidTimeline),
                "timeline project reference");
    }

    @Test
    void rejectsTimelineWithMissingEvidence() {
        String invalidTimeline = timelineJson().replace(
                "\"evidenceIds\": [\"sql-audit-delivery-set\"]",
                "\"evidenceIds\": [\"missing-evidence\"]"
        );
        assertInvalid(validJson().replace(timelineJson(), invalidTimeline),
                "timeline evidence reference");
    }

    @Test
    void rejectsBlankTimelineNarrative() {
        assertInvalid(validJson().replace("\"impact\": \"形成公开交付闭环\"",
                "\"impact\": \" \""), "timeline impact");
    }

    private void assertInvalid(String json, String expectedMessage) {
        assertThatThrownBy(() -> repository(json).getSnapshot())
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining(expectedMessage);
    }

    private JsonPublicPortfolioRepository repository(String json) {
        return new JsonPublicPortfolioRepository(
                objectMapper,
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)),
                validator
        );
    }

    private String validJson() {
        return """
                {
                  "schemaVersion": "2.0",
                  "contentVersion": "2026-07-14.1",
                  "publishedAt": "2026-07-14T00:00:00+08:00",
                  "owner": {
                    "name": "",
                    "role": "Java 后端开发实习生",
                    "summary": "工程实践作品集",
                    "githubUrl": null,
                    "email": null,
                    "resumeUrl": null
                  },
                  "projects": [%s],
                  "claims": [%s],
                  "claimEvidenceLinks": [%s],
                  "questions": [%s],
                  "evidence": [{
                    "id": "sql-audit-delivery-set",
                    "code": "E-01",
                    "title": "SQL 审计工具交付证据集",
                    "type": "COLLECTION",
                    "periodStart": "2026-06-02",
                    "periodEnd": "2026-07-10",
                    "sourceCount": 7,
                    "summary": "脱敏后的连续开发与交付记录",
                    "publicStatus": "APPROVED",
                    "rawContentPublic": false
                  }],
                  "timeline": [%s]
                }
                """.formatted(projectJson(), claimJson(), linkJson(), questionJson(), timelineJson());
    }

    private String claimJson() {
        return """
                {
                  "id": "claim-sql-audit-delivered",
                  "subjectType": "PROJECT",
                  "subjectId": "sql-audit-project",
                  "category": "OUTCOME",
                  "statement": "核心版本已部署并形成使用文档。",
                  "detail": "仅表达经过审核的交付状态。",
                  "achievementStatus": "DELIVERED",
                  "contributionType": "PRIMARY",
                  "verificationBasis": "EVIDENCE_SUPPORTED",
                  "verificationStatus": "VERIFIED",
                  "materiality": "KEY",
                  "topics": ["DELIVERY"],
                  "audiencePriorities": {"INTERVIEWER": 100}
                }
                """;
    }

    private String linkJson() {
        return """
                {
                  "id": "link-delivery-e01",
                  "claimId": "claim-sql-audit-delivered",
                  "evidenceId": "sql-audit-delivery-set",
                  "supportType": "DIRECT",
                  "scope": "证明核心版本已经交付；不证明长期生产效果。",
                  "reviewStatus": "APPROVED"
                }
                """;
    }

    private String projectJson() {
        return """
                {
                  "id": "sql-audit-project",
                  "code": "P-01",
                  "slug": "sql-audit",
                  "title": "SQL 审计与故障排查工具",
                  "summary": "面向远程 SQL 日志检索和追溯的内部工具",
                  "background": "人工排查分散的远程 SQL 日志效率较低。",
                  "responsibilities": ["主导核心功能和多轮迭代"],
                  "solution": "采用异步任务、多目标路由和动态结果分组。",
                  "keyDecisions": ["配置替代硬编码"],
                  "technologies": ["Spring Boot", "Vue"],
                  "verification": ["功能测试", "安全输入测试"],
                  "outcome": "核心版本已部署并形成使用文档。",
                  "handoff": "后续部分优化由同事继续接手。",
                  "status": "DELIVERED",
                  "contributionType": "PRIMARY",
                  "claimIds": ["claim-sql-audit-delivered"],
                  "evidenceIds": ["sql-audit-delivery-set"],
                  "timelineEventIds": ["timeline-sql-audit-delivery"]
                }
                """;
    }

    private String questionJson() {
        return """
                {
                  "id": "sql-audit-overview",
                  "text": "请详细介绍 SQL 审计与故障排查工具项目",
                  "aliases": ["介绍 SQL 审计项目"],
                  "audiences": ["INTERVIEWER"],
                  "projectIds": ["sql-audit-project"],
                  "topics": ["OVERVIEW"],
                  "preferredClaimCategories": ["OUTCOME"],
                  "placements": ["HOME"],
                  "deterministicEntry": true,
                  "displayOrder": 10
                }
                """;
    }

    private String timelineJson() {
        return """
                {
                  "id": "timeline-sql-audit-delivery",
                  "dateLabel": "2026.06–07",
                  "title": "从固定路径查询到可交付工具",
                  "problem": "查询路径被写死",
                  "action": "完成多目标路由和验证",
                  "impact": "形成公开交付闭环",
                  "projectIds": ["sql-audit-project"],
                  "claimIds": ["claim-sql-audit-delivered"],
                  "evidenceIds": ["sql-audit-delivery-set"]
                }
                """;
    }
}
