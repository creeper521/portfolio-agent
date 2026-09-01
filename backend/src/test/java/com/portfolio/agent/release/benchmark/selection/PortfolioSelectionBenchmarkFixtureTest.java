package com.portfolio.agent.release.benchmark.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioSelectionBenchmarkFixtureTest {
    @Test
    void fixtureUsesOnlyRealPublicSubjectsAndCapabilitiesAndAllSplits() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode portfolio;
        JsonNode fixture;
        try (InputStream stream = getClass().getResourceAsStream("/public-data/bundle/portfolio.json")) {
            portfolio = mapper.readTree(stream);
        }
        try (InputStream stream = getClass().getResourceAsStream(
                "/retrieval-benchmark/portfolio-selection-cases.json")) {
            fixture = mapper.readTree(stream);
        }
        Set<String> subjects = new HashSet<>();
        Set<String> projects = new HashSet<>();
        portfolio.path("projects").forEach(node -> {
            String id = node.path("id").asText();
            projects.add(id);
            subjects.add(id);
        });
        portfolio.path("cases").forEach(node -> subjects.add(node.path("id").asText()));
        Set<String> capabilities = new HashSet<>();
        portfolio.path("claims").forEach(claim ->
                claim.path("topics").forEach(topic -> capabilities.add(topic.asText())));
        Set<String> splits = new HashSet<>();
        Set<String> audienceRoles = Set.of(
                "INTERVIEWER", "TECH_INTERVIEWER", "MENTOR", "HR", "GUEST", "PEER_DEVELOPER");
        for (JsonNode benchmarkCase : fixture.path("cases")) {
            splits.add(benchmarkCase.path("split").asText());
            JsonNode target = benchmarkCase.path("target");
            assertThat(audienceRoles).contains(target.path("audienceRole").asText());
            assertThat(target.path("allowedSubjectKinds").isArray()).isTrue();
            java.util.List<String> allowedKinds = new java.util.ArrayList<>();
            target.path("allowedSubjectKinds").forEach(kind -> allowedKinds.add(kind.asText()));
            assertThat(allowedKinds)
                    .isNotEmpty()
                    .allMatch(kind -> Set.of("PROJECT", "CASE").contains(kind));
            if (allowedKinds.equals(java.util.List.of("PROJECT"))) {
                benchmarkCase.path("acceptableSubjectSets").forEach(set ->
                        set.forEach(subject -> assertThat(projects).contains(subject.asText())));
            }
            benchmarkCase.path("acceptableSubjectSets").forEach(set ->
                    set.forEach(subject -> assertThat(subjects).contains(subject.asText())));
            benchmarkCase.path("requiredCapabilities").forEach(capability ->
                    assertThat(capabilities).contains(capability.asText()));
            target.path("capabilityCodes").forEach(capability ->
                    assertThat(capabilities).contains(capability.asText()));
        }
        assertThat(splits).containsExactlyInAnyOrder("CALIBRATION", "HOLDOUT", "REGRESSION");
    }

    @Test
    void selectionTargetJsonRequiresExplicitNonEmptyKnownSubjectKinds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String prefix = """
                {"careerTrack":null,"audienceRole":"HR","capabilityCodes":[],"goal":null,
                """;
        String suffix = ",\"requestedSize\":2}";

        SelectionTarget target = mapper.readValue(
                prefix + "\"allowedSubjectKinds\":[\"PROJECT\"]" + suffix,
                SelectionTarget.class);

        assertThat(target.getAllowedSubjectKinds())
                .containsExactly(PortfolioSubjectKind.PROJECT);
        assertThat(mapper.readValue(mapper.writeValueAsBytes(target), SelectionTarget.class)
                .getAllowedSubjectKinds())
                .containsExactly(PortfolioSubjectKind.PROJECT);
        assertThatThrownBy(() -> mapper.readValue(
                prefix + "\"requestedSize\":2}", SelectionTarget.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue(
                prefix + "\"allowedSubjectKinds\":null" + suffix, SelectionTarget.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue(
                prefix + "\"allowedSubjectKinds\":[]" + suffix, SelectionTarget.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue(
                prefix + "\"allowedSubjectKinds\":[\"UNKNOWN\"]" + suffix,
                SelectionTarget.class))
                .isInstanceOf(Exception.class);
    }
}
