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
        assertInvalid(validJson().replace("\"schemaVersion\": \"1.0\"", "\"schemaVersion\": \"2.0\""),
                "schemaVersion");
    }

    @Test
    void rejectsDuplicateProjectIds() {
        String duplicateProject = projectJson().replace(
                "\"slug\": \"sql-audit\"", "\"slug\": \"sql-audit-copy\"");
        String json = validJson().replace(projectJson(), projectJson() + "," + duplicateProject);

        assertInvalid(json, "duplicate project id");
    }

    @Test
    void rejectsDanglingQuestionReference() {
        assertInvalid(validJson().replace("\"questionIds\": [\"sql-audit-overview\"]",
                "\"questionIds\": [\"missing-question\"]"), "question reference");
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
    void rejectsBlankProjectContent() {
        assertInvalid(validJson().replace("\"title\": \"SQL 审计与故障排查工具\"",
                "\"title\": \"  \""), "project title");
    }

    @Test
    void rejectsProjectWithoutEvidenceAssociation() {
        assertInvalid(validJson().replace("\"evidenceIds\": [\"sql-audit-delivery-set\"]",
                "\"evidenceIds\": []"), "project evidenceIds");
    }

    @Test
    void rejectsEvidenceWithReversedPeriod() {
        assertInvalid(validJson().replace("\"periodEnd\": \"2026-07-10\"",
                "\"periodEnd\": \"2026-05-01\""), "period");
    }

    @Test
    void rejectsQuestionWithoutSuggestion() {
        assertInvalid(validJson().replace("\"suggestion\": \"详细介绍 SQL 审计项目\"",
                "\"suggestion\": \"\""), "suggestion");
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
                  "schemaVersion": "1.0",
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
                  "questions": [{
                    "id": "sql-audit-overview",
                    "projectId": "sql-audit-project",
                    "canonicalQuestion": "请详细介绍 SQL 审计与故障排查工具项目",
                    "aliases": ["介绍 SQL 审计项目"],
                    "suggestion": "详细介绍 SQL 审计项目"
                  }],
                  "evidence": [{
                    "id": "sql-audit-delivery-set",
                    "title": "SQL 审计工具交付证据集",
                    "type": "COLLECTION",
                    "periodStart": "2026-06-02",
                    "periodEnd": "2026-07-10",
                    "sourceCount": 7,
                    "summary": "脱敏后的连续开发与交付记录",
                    "supportedClaims": ["核心版本已部署并形成使用文档"],
                    "publicStatus": "APPROVED",
                    "rawContentPublic": false
                  }],
                  "timeline": []
                }
                """.formatted(projectJson());
    }

    private String projectJson() {
        return """
                {
                  "id": "sql-audit-project",
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
                  "questionIds": ["sql-audit-overview"],
                  "evidenceIds": ["sql-audit-delivery-set"]
                }
                """;
    }
}
