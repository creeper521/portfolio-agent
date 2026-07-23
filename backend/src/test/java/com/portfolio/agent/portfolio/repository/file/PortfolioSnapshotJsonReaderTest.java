package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSnapshotJsonReaderTest {

    private final PortfolioSnapshotJsonReader reader =
            new PortfolioSnapshotJsonReader(new ObjectMapper().findAndRegisterModules());

    @Test
    void schemaTwoNormalizesMissingCaseFieldsToEmptyLists() {
        PortfolioSnapshot snapshot = reader.readBundle(schemaTwoPortfolioBytes());

        assertThat(snapshot.getSchemaVersion()).isEqualTo("2.0");
        assertThat(snapshot.getCases()).isEmpty();
        assertThat(snapshot.getQuestions()).allSatisfy(question ->
                assertThat(question.getCaseIds()).isEmpty());
        assertThat(snapshot.getTimeline()).allSatisfy(event ->
                assertThat(event.getCaseIds()).isEmpty());
    }

    @Test
    void schemaThreeRequiresCasesAtTheTopLevel() {
        assertThatThrownBy(() -> reader.readBundle(schemaThreeWithoutCasesBytes()))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("cases is required for schemaVersion 3.0");
    }

    @Test
    void schemaThreeRejectsUnknownTopLevelFields() {
        assertThatThrownBy(() -> reader.readBundle(schemaThreeWithField("internalNotes", "secret")))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("portfolio.json field set is not canonical");
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        assertThatThrownBy(() -> reader.readBundle(portfolioBytesWithSchema("4.0")))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("unsupported schemaVersion: 4.0");
    }

    @Test
    void schemaThreeRequiresNestedCaseIdArrays() {
        assertThatThrownBy(() -> reader.readBundle(bytes(canonicalJson(
                "3.0", ",\"cases\":[]", "", ",\"caseIds\":[]"))))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("[0].caseIds");
    }

    @Test
    void schemaThreeRejectsNonArrayNestedCaseIds() {
        assertThatThrownBy(() -> reader.readBundle(bytes(canonicalJson(
                "3.0", ",\"cases\":[]", ",\"caseIds\":\"case-1\"", ",\"caseIds\":[]"))))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("[0].caseIds");
    }

    @Test
    void legacyResourceRenamesAliasesAndNormalizesCaseFields() {
        PortfolioSnapshot snapshot = reader.readLegacyResource(bytes("""
                {
                  "schemaVersion":"2.0",
                  "contentVersion":"legacy-1",
                  "publishedAt":"2026-07-17T00:00:00+08:00",
                  "owner":%s,
                  "projects":[],
                  "claims":[],
                  "evidence":[],
                  "claimEvidenceLinks":[],
                  "questions":[%s],
                  "timeline":[%s]
                }
                """.formatted(ownerJson(), questionJson(""), timelineJson(""))));

        assertThat(snapshot.getPublishedAt()).isNotNull();
        assertThat(snapshot.getCases()).isEmpty();
        assertThat(snapshot.getQuestions()).singleElement().satisfies(question ->
                assertThat(question.getCaseIds()).isEmpty());
        assertThat(snapshot.getTimeline()).singleElement().satisfies(event ->
                assertThat(event.getCaseIds()).isEmpty());
    }

    @Test
    void bundleRejectsLegacyPublishedAtField() {
        String json = canonicalJson("2.0", "", "", "");
        String withPublishedAt = json.replace(
                "\"contentVersion\":\"content-1\",",
                "\"contentVersion\":\"content-1\",\"publishedAt\":\"2026-07-17T00:00:00+08:00\",");

        assertThatThrownBy(() -> reader.readBundle(bytes(withPublishedAt)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("portfolio.json field set is not canonical");
    }

    @Test
    void wrapsMalformedJsonAsInvalidSnapshot() {
        assertThatThrownBy(() -> reader.readBundle(bytes("{")))
                .isInstanceOf(InvalidPortfolioSnapshotException.class);
    }

    private byte[] schemaTwoPortfolioBytes() {
        return bytes(canonicalJson("2.0", "", "", ""));
    }

    private byte[] schemaThreeWithoutCasesBytes() {
        return bytes(canonicalJson("3.0", "", ",\"caseIds\":[]", ",\"caseIds\":[]"));
    }

    private byte[] schemaThreeWithField(String name, String value) {
        String json = canonicalJson(
                "3.0", ",\"cases\":[]", ",\"caseIds\":[]", ",\"caseIds\":[]");
        return bytes(json.replaceFirst("\\{", "{\"" + name + "\":\"" + value + "\","));
    }

    private byte[] portfolioBytesWithSchema(String schemaVersion) {
        return bytes(canonicalJson(
                schemaVersion, ",\"cases\":[]", ",\"caseIds\":[]", ",\"caseIds\":[]"));
    }

    private String canonicalJson(
            String schemaVersion,
            String caseField,
            String questionCaseField,
            String timelineCaseField
    ) {
        return """
                {
                  "schemaVersion":"%s",
                  "contentVersion":"content-1",
                  "owner":%s,
                  "projects":[]%s,
                  "claims":[],
                  "evidence":[],
                  "claimEvidenceLinks":[],
                  "timelineEvents":[%s],
                  "questionPresets":[%s]
                }
                """.formatted(
                schemaVersion,
                ownerJson(),
                caseField,
                timelineJson(timelineCaseField),
                questionJson(questionCaseField));
    }

    private String ownerJson() {
        return """
                {"name":"Owner","role":"Engineer","summary":"Summary",
                "githubUrl":null,"email":null,"resumeUrl":null}
                """;
    }

    private String questionJson(String caseField) {
        return """
                {"id":"question-1","text":"Question","aliases":[],"audiences":[],
                "projectIds":[]%s,"topics":[],"preferredClaimCategories":[],
                "placements":[],"deterministicEntry":true,"displayOrder":1}
                """.formatted(caseField);
    }

    private String timelineJson(String caseField) {
        return """
                {"id":"timeline-1","dateLabel":"2026","title":"Title","problem":"Problem",
                "action":"Action","impact":"Impact","projectIds":[]%s,"claimIds":[],
                "evidenceIds":[]}
                """.formatted(caseField);
    }

    private byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
