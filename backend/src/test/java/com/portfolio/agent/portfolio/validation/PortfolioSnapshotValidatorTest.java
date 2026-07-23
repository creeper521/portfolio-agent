package com.portfolio.agent.portfolio.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
import com.portfolio.agent.portfolio.repository.file.PortfolioSnapshotJsonReader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSnapshotValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PortfolioSnapshotValidator validator = new PortfolioSnapshotValidator();

    @Test
    void acceptsSchemaTwoNormalizedCompatibilitySnapshot() {
        PortfolioSnapshot snapshot = new PortfolioSnapshotJsonReader(objectMapper)
                .readLegacyResource(legacySchemaTwoJson().getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> validator.validate(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void acceptsSchemaThreeCaseOwnedContent() {
        assertThatCode(() -> validate(validSchemaThreeJson())).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertInvalid(validJson().replace(
                "\"schemaVersion\": \"2.0\"", "\"schemaVersion\": \"4.0\""),
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
        assertInvalid(validJson().replace(
                "\"supportType\": \"DIRECT\"", "\"supportType\": \"CONTEXTUAL\""),
                "DIRECT");
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
        String json = validJson().replace(
                projectJson(), projectJson() + "," + duplicateProject);

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
        assertInvalid(validJson().replace(
                "\"projectIds\": [\"sql-audit-project\"]",
                "\"projectIds\": [\"missing-project\"]"),
                "question project reference");
    }

    @Test
    void rejectsEvidenceThatIsNotApproved() {
        assertInvalid(validJson().replace(
                "\"publicStatus\": \"APPROVED\"", "\"publicStatus\": \"PENDING\""),
                "APPROVED");
    }

    @Test
    void rejectsEvidenceThatExposesRawContent() {
        assertInvalid(validJson().replace(
                "\"rawContentPublic\": false", "\"rawContentPublic\": true"),
                "raw content");
    }

    @Test
    void rejectsEvidenceWithoutExplicitRawContentFlag() {
        assertInvalid(validJson().replace(
                "\"rawContentPublic\": false", "\"rawContentPublic\": null"),
                "rawContentPublic");
    }

    @Test
    void rejectsBlankEvidenceCode() {
        assertInvalid(validJson().replace("\"code\": \"E-01\"", "\"code\": \" \""),
                "evidence code");
    }

    @Test
    void rejectsBlankProjectContent() {
        assertInvalid(validJson().replace(
                "\"title\": \"SQL audit and troubleshooting tool\"",
                "\"title\": \"  \""),
                "project title");
    }

    @Test
    void rejectsProjectWithoutEvidenceAssociation() {
        String invalidProject = projectJson().replace(
                "\"evidenceIds\": [\"sql-audit-delivery-set\"]", "\"evidenceIds\": []");

        assertInvalid(validJson().replace(projectJson(), invalidProject),
                "project evidenceIds");
    }

    @Test
    void rejectsDuplicateTimelineIds() {
        String json = validJson().replace(
                timelineJson(), timelineJson() + "," + timelineJson());

        assertInvalid(json, "duplicate timeline id");
    }

    @Test
    void rejectsEvidenceWithReversedPeriod() {
        assertInvalid(validJson().replace(
                "\"periodEnd\": \"2026-07-10\"", "\"periodEnd\": \"2026-05-01\""),
                "period");
    }

    @Test
    void rejectsQuestionWithoutText() {
        assertInvalid(validJson().replace(
                "\"text\": \"Describe the SQL audit project\"",
                "\"text\": \"\""),
                "question text");
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
                "\"evidenceIds\": [\"missing-evidence\"]");

        assertInvalid(validJson().replace(timelineJson(), invalidTimeline),
                "timeline evidence reference");
    }

    @Test
    void rejectsBlankTimelineNarrative() {
        assertInvalid(validJson().replace(
                "\"impact\": \"Created a public delivery loop\"",
                "\"impact\": \" \""),
                "timeline impact");
    }

    @Test
    void rejectsQuestionWithoutProjectOrCase() {
        String invalidQuestion = caseQuestionJson().replace(
                "\"caseIds\": [\"case-one\"]", "\"caseIds\": []");

        assertInvalid(validSchemaThreeJson().replace(caseQuestionJson(), invalidQuestion),
                "question must reference at least one project or case");
    }

    @Test
    void rejectsTimelineWithoutProjectOrCase() {
        String invalidTimeline = caseTimelineJson()
                .replace("\"projectIds\": [\"sql-audit-project\"]", "\"projectIds\": []")
                .replace("\"caseIds\": [\"case-one\"]", "\"caseIds\": []");

        assertInvalid(validSchemaThreeJson().replace(caseTimelineJson(), invalidTimeline),
                "timeline must reference at least one project or case");
    }

    @Test
    void rejectsQuestionWithUnknownCase() {
        String invalidQuestion = caseQuestionJson().replace(
                "\"caseIds\": [\"case-one\"]", "\"caseIds\": [\"case-missing\"]");

        assertInvalid(validSchemaThreeJson().replace(caseQuestionJson(), invalidQuestion),
                "question case reference does not exist: case-missing");
    }

    @Test
    void rejectsTimelineWithUnknownCase() {
        String invalidTimeline = caseTimelineJson().replace(
                "\"caseIds\": [\"case-one\"]", "\"caseIds\": [\"case-missing\"]");

        assertInvalid(validSchemaThreeJson().replace(caseTimelineJson(), invalidTimeline),
                "timeline case reference does not exist: case-missing");
    }

    @Test
    void rejectsCaseClaimWithUnknownSubject() {
        String invalidClaim = caseClaimJson().replace(
                "\"subjectId\": \"case-one\"", "\"subjectId\": \"case-missing\"");

        assertInvalid(validSchemaThreeJson().replace(caseClaimJson(), invalidClaim),
                "claim subject reference does not exist: case-missing");
    }

    @Test
    void rejectsCaseClaimOwnedByAnotherSubject() {
        String invalidCase = caseJson().replace(
                "\"claimIds\": [\"claim-case-delivered\"]",
                "\"claimIds\": [\"claim-sql-audit-delivered\"]");

        assertInvalid(validSchemaThreeJson().replace(caseJson(), invalidCase),
                "claim reference belongs to a different case");
    }

    @Test
    void rejectsDuplicateCaseId() {
        String duplicate = caseJson()
                .replace("\"code\": \"CASE-01\"", "\"code\": \"CASE-02\"")
                .replace("\"slug\": \"case-one\"", "\"slug\": \"case-two\"");

        assertInvalid(withAdditionalCase(duplicate), "duplicate case id");
    }

    @Test
    void rejectsDuplicateCaseCode() {
        String duplicate = caseJson()
                .replace("\"id\": \"case-one\"", "\"id\": \"case-two\"")
                .replace("\"slug\": \"case-one\"", "\"slug\": \"case-two\"");

        assertInvalid(withAdditionalCase(duplicate), "duplicate case code");
    }

    @Test
    void rejectsDuplicateCaseSlug() {
        String duplicate = caseJson()
                .replace("\"id\": \"case-one\"", "\"id\": \"case-two\"")
                .replace("\"code\": \"CASE-01\"", "\"code\": \"CASE-02\"");

        assertInvalid(withAdditionalCase(duplicate), "duplicate case slug");
    }

    @Test
    void rejectsIllegalCaseSlug() {
        assertInvalid(withCase(caseJson().replace(
                "\"slug\": \"case-one\"", "\"slug\": \"Case One\"")),
                "case slug format is invalid");
    }

    @Test
    void rejectsBlankCaseRequiredField() {
        assertInvalid(withCase(caseJson().replace(
                "\"title\": \"Case one\"", "\"title\": \"  \"")),
                "case title is required");
    }

    @Test
    void acceptsCaseWithEmptyDecisions() {
        assertThatCode(() -> validate(withCase(caseJson().replace(
                "\"decisions\": [\"Keep compatibility\"]", "\"decisions\": []"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCaseWithoutActions() {
        assertInvalid(withCase(caseJson().replace(
                "\"actions\": [\"Implement validation\"]", "\"actions\": []")),
                "case actions");
    }

    @Test
    void rejectsCaseWithoutVerification() {
        assertInvalid(withCase(caseJson().replace(
                "\"verification\": [\"Focused tests\"]", "\"verification\": []")),
                "case verification");
    }

    @Test
    void rejectsCaseWithoutLimitations() {
        assertInvalid(withCase(caseJson().replace(
                "\"limitations\": [\"Public data only\"]", "\"limitations\": []")),
                "case limitations");
    }

    @Test
    void rejectsCaseWithoutClaimAssociation() {
        assertInvalid(withCase(caseJson().replace(
                "\"claimIds\": [\"claim-case-delivered\"]", "\"claimIds\": []")),
                "case claimIds");
    }

    @Test
    void rejectsCaseWithoutEvidenceAssociation() {
        assertInvalid(withCase(caseJson().replace(
                "\"evidenceIds\": [\"sql-audit-delivery-set\"]", "\"evidenceIds\": []")),
                "case evidenceIds");
    }

    @Test
    void rejectsCaseWithoutTimelineAssociation() {
        assertInvalid(withCase(caseJson().replace(
                "\"timelineEventIds\": [\"timeline-case-delivery\"]",
                "\"timelineEventIds\": []")),
                "case timelineEventIds");
    }

    @Test
    void rejectsCaseWithoutQuestionAssociation() {
        assertInvalid(withCase(caseJson().replace(
                "\"questionPresetIds\": [\"case-overview\"]",
                "\"questionPresetIds\": []")),
                "case questionPresetIds");
    }

    @Test
    void rejectsCaseWithoutType() {
        assertInvalid(withCase(caseJson().replace(
                "\"type\": \"FEATURE\"", "\"type\": null")),
                "case type is required");
    }

    @Test
    void rejectsCaseWithoutAchievementStatus() {
        assertInvalid(withCase(caseJson().replace(
                "\"achievementStatus\": \"DELIVERED\"", "\"achievementStatus\": null")),
                "case achievementStatus is required");
    }

    @Test
    void rejectsCaseWithoutContributionType() {
        assertInvalid(withCase(caseJson().replace(
                "\"contributionType\": \"PRIMARY\"", "\"contributionType\": null")),
                "case contributionType is required");
    }

    @Test
    void rejectsMissingCaseProject() {
        assertInvalid(withCase(caseJson().replace(
                "\"projectId\": null", "\"projectId\": \"project-missing\"")),
                "case project reference does not exist");
    }

    @Test
    void rejectsMissingCaseEvidence() {
        assertInvalid(withCase(caseJson().replace(
                "\"evidenceIds\": [\"sql-audit-delivery-set\"]",
                "\"evidenceIds\": [\"evidence-missing\"]")),
                "case evidence reference does not exist");
    }

    @Test
    void rejectsMissingCaseTimeline() {
        assertInvalid(withCase(caseJson().replace(
                "\"timelineEventIds\": [\"timeline-case-delivery\"]",
                "\"timelineEventIds\": [\"timeline-missing\"]")),
                "case timeline reference does not exist");
    }

    @Test
    void rejectsMissingCaseQuestionPreset() {
        assertInvalid(withCase(caseJson().replace(
                "\"questionPresetIds\": [\"case-overview\"]",
                "\"questionPresetIds\": [\"question-missing\"]")),
                "case question reference does not exist");
    }

    @Test
    void rejectsCaseAchievementWithoutDirectLink() {
        String withoutCaseLink = validSchemaThreeJson().replace(
                "," + caseLinkJson(), "");

        assertInvalid(withoutCaseLink,
                "achievement claim requires an APPROVED DIRECT link");
    }

    private void assertInvalid(String json, String expectedMessage) {
        assertThatThrownBy(() -> validate(json))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining(expectedMessage);
    }

    private void validate(String json) {
        repository(json).getSnapshot();
    }

    private JsonPublicPortfolioRepository repository(String json) {
        return new JsonPublicPortfolioRepository(
                objectMapper,
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)),
                validator
        );
    }

    private String withCase(String replacement) {
        return validSchemaThreeJson().replace(caseJson(), replacement);
    }

    private String withAdditionalCase(String additionalCase) {
        return validSchemaThreeJson().replace(
                "\"cases\": [%s]".formatted(caseJson()),
                "\"cases\": [%s,%s]".formatted(caseJson(), additionalCase));
    }

    private String validJson() {
        return """
                {
                  "schemaVersion": "2.0",
                  "contentVersion": "2026-07-14.1",
                  "publishedAt": "2026-07-14T00:00:00+08:00",
                  "owner": {
                    "name": "",
                    "role": "Java backend intern",
                    "summary": "Engineering portfolio",
                    "githubUrl": null,
                    "email": null,
                    "resumeUrl": null
                  },
                  "projects": [%s],
                  "cases": [],
                  "claims": [%s],
                  "claimEvidenceLinks": [%s],
                  "questions": [%s],
                  "evidence": [%s],
                  "timeline": [%s]
                }
                """.formatted(
                projectJson(),
                projectClaimJson(),
                projectLinkJson(),
                questionJson(),
                evidenceJson(),
                timelineJson());
    }

    private String legacySchemaTwoJson() {
        return validJson()
                .replace("  \"cases\": [],\n", "")
                .replace("  \"caseIds\": [],\n", "");
    }

    private String validSchemaThreeJson() {
        return """
                {
                  "schemaVersion": "3.0",
                  "contentVersion": "2026-07-23.1",
                  "publishedAt": "2026-07-23T00:00:00+08:00",
                  "owner": {
                    "name": "",
                    "role": "Java backend intern",
                    "summary": "Engineering portfolio",
                    "githubUrl": null,
                    "email": null,
                    "resumeUrl": null
                  },
                  "projects": [%s],
                  "cases": [%s],
                  "claims": [%s,%s],
                  "claimEvidenceLinks": [%s,%s],
                  "questions": [%s],
                  "evidence": [%s],
                  "timeline": [%s]
                }
                """.formatted(
                projectJson(),
                caseJson(),
                projectClaimJson(),
                caseClaimJson(),
                projectLinkJson(),
                caseLinkJson(),
                caseQuestionJson(),
                evidenceJson(),
                caseTimelineJson());
    }

    private String projectClaimJson() {
        return """
                {
                  "id": "claim-sql-audit-delivered",
                  "subjectType": "PROJECT",
                  "subjectId": "sql-audit-project",
                  "category": "OUTCOME",
                  "statement": "The core version was delivered",
                  "detail": "Only the reviewed delivery status is stated",
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

    private String caseClaimJson() {
        return """
                {
                  "id": "claim-case-delivered",
                  "subjectType": "CASE",
                  "subjectId": "case-one",
                  "category": "OUTCOME",
                  "statement": "The case behavior was delivered",
                  "detail": "Only the reviewed case status is stated",
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

    private String projectLinkJson() {
        return """
                {
                  "id": "link-delivery-e01",
                  "claimId": "claim-sql-audit-delivered",
                  "evidenceId": "sql-audit-delivery-set",
                  "supportType": "DIRECT",
                  "scope": "Proves delivery, not long-term production impact",
                  "reviewStatus": "APPROVED"
                }
                """;
    }

    private String caseLinkJson() {
        return """
                {
                  "id": "link-case-delivery-e01",
                  "claimId": "claim-case-delivered",
                  "evidenceId": "sql-audit-delivery-set",
                  "supportType": "DIRECT",
                  "scope": "Proves the case delivery status",
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
                  "title": "SQL audit and troubleshooting tool",
                  "summary": "Internal remote SQL log search and tracing tool",
                  "background": "Manual remote SQL log investigation was inefficient",
                  "responsibilities": ["Led core features and iterations"],
                  "solution": "Used asynchronous jobs and multi-target routing",
                  "keyDecisions": ["Use configuration instead of hard coding"],
                  "technologies": ["Spring Boot", "Vue"],
                  "verification": ["Functional tests", "Input security tests"],
                  "outcome": "The core version was delivered with documentation",
                  "handoff": "Further optimization was handed to teammates",
                  "status": "DELIVERED",
                  "contributionType": "PRIMARY",
                  "claimIds": ["claim-sql-audit-delivered"],
                  "evidenceIds": ["sql-audit-delivery-set"],
                  "timelineEventIds": ["timeline-case-delivery"]
                }
                """;
    }

    private String caseJson() {
        return """
                {
                  "id": "case-one",
                  "code": "CASE-01",
                  "slug": "case-one",
                  "type": "FEATURE",
                  "title": "Case one",
                  "summary": "A focused public case study",
                  "problem": "The behavior needed an explicit public contract",
                  "actions": ["Implement validation"],
                  "decisions": ["Keep compatibility"],
                  "verification": ["Focused tests"],
                  "outcome": "The case contract is explicit",
                  "limitations": ["Public data only"],
                  "achievementStatus": "DELIVERED",
                  "contributionType": "PRIMARY",
                  "projectId": null,
                  "claimIds": ["claim-case-delivered"],
                  "evidenceIds": ["sql-audit-delivery-set"],
                  "timelineEventIds": ["timeline-case-delivery"],
                  "questionPresetIds": ["case-overview"]
                }
                """;
    }

    private String questionJson() {
        return """
                {
                  "id": "sql-audit-overview",
                  "text": "Describe the SQL audit project",
                  "aliases": ["Introduce the SQL audit project"],
                  "audiences": ["INTERVIEWER"],
                  "projectIds": ["sql-audit-project"],
                  "caseIds": [],
                  "topics": ["OVERVIEW"],
                  "preferredClaimCategories": ["OUTCOME"],
                  "placements": ["HOME"],
                  "deterministicEntry": true,
                  "displayOrder": 10
                }
                """;
    }

    private String caseQuestionJson() {
        return """
                {
                  "id": "case-overview",
                  "text": "Describe case one",
                  "aliases": ["Introduce case one"],
                  "audiences": ["INTERVIEWER"],
                  "projectIds": [],
                  "caseIds": ["case-one"],
                  "topics": ["OVERVIEW"],
                  "preferredClaimCategories": ["OUTCOME"],
                  "placements": ["HOME"],
                  "deterministicEntry": true,
                  "displayOrder": 20
                }
                """;
    }

    private String evidenceJson() {
        return """
                {
                  "id": "sql-audit-delivery-set",
                  "code": "E-01",
                  "title": "SQL audit delivery evidence",
                  "type": "COLLECTION",
                  "periodStart": "2026-06-02",
                  "periodEnd": "2026-07-10",
                  "sourceCount": 7,
                  "summary": "Sanitized development and delivery records",
                  "publicStatus": "APPROVED",
                  "rawContentPublic": false
                }
                """;
    }

    private String timelineJson() {
        return """
                {
                  "id": "timeline-case-delivery",
                  "dateLabel": "2026.06-07",
                  "title": "From fixed paths to a deliverable tool",
                  "problem": "Query paths were hard coded",
                  "action": "Implemented routing and verification",
                  "impact": "Created a public delivery loop",
                  "projectIds": ["sql-audit-project"],
                  "caseIds": [],
                  "claimIds": ["claim-sql-audit-delivered"],
                  "evidenceIds": ["sql-audit-delivery-set"]
                }
                """;
    }

    private String caseTimelineJson() {
        return """
                {
                  "id": "timeline-case-delivery",
                  "dateLabel": "2026.06-07",
                  "title": "Case contract delivery",
                  "problem": "Case references were not validated",
                  "action": "Implemented ownership and association checks",
                  "impact": "Case references are explicit",
                  "projectIds": ["sql-audit-project"],
                  "caseIds": ["case-one"],
                  "claimIds": ["claim-sql-audit-delivered", "claim-case-delivered"],
                  "evidenceIds": ["sql-audit-delivery-set"]
                }
                """;
    }
}
