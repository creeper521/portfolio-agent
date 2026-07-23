package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSnapshotJsonReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PortfolioSnapshotJsonReader reader =
            new PortfolioSnapshotJsonReader(objectMapper);

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
    void schemaTwoClearsExistingCaseCollections() {
        PortfolioSnapshot snapshot = reader.readBundle(bytes(canonicalJson(
                "2.0", ",\"cases\":[" + caseStudyJson() + "]", "", "")));

        assertThat(snapshot.getCases()).isEmpty();
    }

    @Test
    void schemaTwoClearsNonArrayCasesAndExistingNestedCaseIds() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(schemaTwoPortfolioBytes());
        root.put("cases", "not-an-array");
        ((ObjectNode) root.withArray("questionPresets").get(0))
                .putArray("caseIds").add("case-hostile");
        ((ObjectNode) root.withArray("timelineEvents").get(0))
                .put("caseIds", "case-hostile");

        PortfolioSnapshot snapshot = reader.readBundle(objectMapper.writeValueAsBytes(root));

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
    void bundleRequiresQuestionPresets() throws Exception {
        ObjectNode root = schemaTwoRoot();
        root.remove("questionPresets");

        assertThatThrownBy(() -> reader.readBundle(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("questionPresets is required and must be an array");
    }

    @Test
    void bundleRequiresTimelineEvents() throws Exception {
        ObjectNode root = schemaTwoRoot();
        root.remove("timelineEvents");

        assertThatThrownBy(() -> reader.readBundle(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("timelineEvents is required and must be an array");
    }

    @Test
    void bundleRejectsNonArrayQuestionPresets() throws Exception {
        ObjectNode root = schemaThreeRoot();
        root.put("questionPresets", "not-an-array");

        assertThatThrownBy(() -> reader.readBundle(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("questionPresets is required and must be an array");
    }

    @Test
    void bundleRejectsNonArrayTimelineEvents() throws Exception {
        ObjectNode root = schemaThreeRoot();
        root.put("timelineEvents", "not-an-array");

        assertThatThrownBy(() -> reader.readBundle(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("timelineEvents is required and must be an array");
    }

    @Test
    void schemaThreeRejectsNonArrayCases() throws Exception {
        ObjectNode root = schemaThreeRoot();
        root.put("cases", "not-an-array");

        assertThatThrownBy(() -> reader.readBundle(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("cases is required and must be an array");
    }

    @Test
    void legacyResourceRenamesAliasesAndNormalizesCaseFields() {
        PortfolioSnapshot snapshot = reader.readLegacyResource(legacyResourceBytes());

        assertThat(snapshot.getPublishedAt()).isNotNull();
        assertThat(snapshot.getCases()).isEmpty();
        assertThat(snapshot.getQuestions()).singleElement().satisfies(question ->
                assertThat(question.getCaseIds()).isEmpty());
        assertThat(snapshot.getTimeline()).singleElement().satisfies(event ->
                assertThat(event.getCaseIds()).isEmpty());
    }

    @Test
    void legacyResourceRequiresQuestions() throws Exception {
        ObjectNode root = legacyRoot();
        root.remove("questions");

        assertThatThrownBy(() -> reader.readLegacyResource(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("questions is required and must be an array");
    }

    @Test
    void legacyResourceRequiresTimeline() throws Exception {
        ObjectNode root = legacyRoot();
        root.remove("timeline");

        assertThatThrownBy(() -> reader.readLegacyResource(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("timeline is required and must be an array");
    }

    @Test
    void legacyResourceRejectsNonArrayQuestions() throws Exception {
        ObjectNode root = legacyRoot();
        root.put("questions", "not-an-array");

        assertThatThrownBy(() -> reader.readLegacyResource(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("questions is required and must be an array");
    }

    @Test
    void legacyResourceRejectsNonArrayTimeline() throws Exception {
        ObjectNode root = legacyRoot();
        root.put("timeline", "not-an-array");

        assertThatThrownBy(() -> reader.readLegacyResource(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("timeline is required and must be an array");
    }

    @Test
    void legacyResourceRejectsUnknownField() throws Exception {
        ObjectNode root = legacyRoot();
        root.put("internalNotes", "secret");

        assertThatThrownBy(() -> reader.readLegacyResource(objectMapper.writeValueAsBytes(root)))
                .isInstanceOf(InvalidPortfolioSnapshotException.class)
                .hasMessageContaining("legacy portfolio resource field set is not canonical");
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

    private ObjectNode schemaTwoRoot() throws Exception {
        return (ObjectNode) objectMapper.readTree(schemaTwoPortfolioBytes());
    }

    private ObjectNode schemaThreeRoot() throws Exception {
        return (ObjectNode) objectMapper.readTree(portfolioBytesWithSchema("3.0"));
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

    private byte[] legacyResourceBytes() {
        return bytes("""
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
                """.formatted(ownerJson(), questionJson(""), timelineJson("")));
    }

    private ObjectNode legacyRoot() throws Exception {
        return (ObjectNode) objectMapper.readTree(legacyResourceBytes());
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

    private String caseStudyJson() {
        return """
                {"id":"case-1","code":"CASE-01","slug":"case-one","type":"FEATURE",
                "title":"Case one","summary":"Summary","problem":"Problem","actions":[],
                "decisions":[],"verification":[],"outcome":"Outcome","limitations":[],
                "achievementStatus":"DELIVERED","contributionType":"PRIMARY",
                "projectId":null,"claimIds":[],"evidenceIds":[],"timelineEventIds":[],
                "questionPresetIds":[]}
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
