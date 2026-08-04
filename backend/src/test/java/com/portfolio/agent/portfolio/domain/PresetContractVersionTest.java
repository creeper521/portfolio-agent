package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresetContractVersionTest {

    @Test
    void ignoresAliasOrderButChangesWhenRequiredClaimsChange() {
        QuestionEvidenceRequirement requirement = new QuestionEvidenceRequirement(1, true);
        String first = PresetContractVersion.calculate(
                "preset-a", "Canonical?", List.of("Alias B", "Alias A"),
                List.of("project-a"), List.of(),
                List.of("claim-1"), List.of("claim-2"), requirement);
        String reordered = PresetContractVersion.calculate(
                "preset-a", "Canonical?", List.of("Alias A", "Alias B"),
                List.of("project-a"), List.of(),
                List.of("claim-1"), List.of("claim-2"), requirement);
        String changed = PresetContractVersion.calculate(
                "preset-a", "Canonical?", List.of("Alias A", "Alias B"),
                List.of("project-a"), List.of(),
                List.of("claim-3"), List.of("claim-2"), requirement);

        assertThat(first).matches("pcv1-[a-f0-9]{16}");
        assertThat(reordered).isEqualTo(first);
        assertThat(changed).isNotEqualTo(first);
    }
}
