package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        portfolio.path("projects").forEach(node -> subjects.add(node.path("id").asText()));
        portfolio.path("cases").forEach(node -> subjects.add(node.path("id").asText()));
        Set<String> capabilities = new HashSet<>();
        portfolio.path("claims").forEach(claim ->
                claim.path("topics").forEach(topic -> capabilities.add(topic.asText())));
        Set<String> splits = new HashSet<>();
        Set<String> audienceRoles = Set.of(
                "INTERVIEWER", "TECH_INTERVIEWER", "MENTOR", "HR", "GUEST", "PEER_DEVELOPER");
        for (JsonNode benchmarkCase : fixture.path("cases")) {
            splits.add(benchmarkCase.path("split").asText());
            assertThat(audienceRoles).contains(benchmarkCase.path("target").path("audienceRole").asText());
            benchmarkCase.path("acceptableSubjectSets").forEach(set ->
                    set.forEach(subject -> assertThat(subjects).contains(subject.asText())));
            benchmarkCase.path("requiredCapabilities").forEach(capability ->
                    assertThat(capabilities).contains(capability.asText()));
            benchmarkCase.path("target").path("capabilityCodes").forEach(capability ->
                    assertThat(capabilities).contains(capability.asText()));
        }
        assertThat(splits).containsExactlyInAnyOrder("CALIBRATION", "HOLDOUT", "REGRESSION");
    }
}
