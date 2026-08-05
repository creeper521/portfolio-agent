package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresetContractVersionTest {

    private static final QuestionEvidenceRequirement REQUIREMENT =
            new QuestionEvidenceRequirement(1, true);

    @Test
    void matchesExpectedFormat() {
        assertThat(version("subject-a", List.of("alias-a")))
                .matches("pcv1-[a-f0-9]{16}");
    }

    @Test
    void changesWhenContractSubjectChanges() {
        assertThat(version("subject-a", List.of("alias-a", "alias-b")))
                .isNotEqualTo(version("subject-b", List.of("alias-a", "alias-b")));
    }

    @Test
    void changesWhenAliasOrderChanges() {
        assertThat(version("subject-a", List.of("alias-a", "alias-b")))
                .isNotEqualTo(version("subject-a", List.of("alias-b", "alias-a")));
    }

    @Test
    void ignoresDisplayAssociations() {
        assertThat(versionWithDisplayAssociations(
                "subject-a", List.of("alias-a"), List.of("project-a"), List.of()))
                .isEqualTo(versionWithDisplayAssociations(
                        "subject-a", List.of("alias-a"),
                        List.of("project-a", "project-b"), List.of("case-a")));
    }

    @Test
    void changesWhenStatusChanges() {
        assertThat(versionWithStatus("subject-a", List.of("alias-a"),
                PresetContractStatus.ACTIVE))
                .isNotEqualTo(versionWithStatus("subject-a", List.of("alias-a"),
                        PresetContractStatus.DRAFT));
    }

    @Test
    void changesWhenRequiredClaimOrderChanges() {
        assertThat(version("subject-a", List.of("alias-a"),
                List.of("claim-1", "claim-2"), List.of("claim-3")))
                .isNotEqualTo(version("subject-a", List.of("alias-a"),
                        List.of("claim-2", "claim-1"), List.of("claim-3")));
    }

    @Test
    void changesWhenSupportingClaimOrderChanges() {
        assertThat(version("subject-a", List.of("alias-a"),
                List.of("claim-1"), List.of("claim-2", "claim-3")))
                .isNotEqualTo(version("subject-a", List.of("alias-a"),
                        List.of("claim-1"), List.of("claim-3", "claim-2")));
    }

    private static String version(String subject, List<String> aliases) {
        return version(subject, aliases, List.of("claim-1"), List.of());
    }

    private static String version(
            String subject,
            List<String> aliases,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds
    ) {
        return versionWithStatusAndAssociations(subject, aliases, PresetContractStatus.ACTIVE,
                List.of("project-a"), List.of(), requiredClaimIds, supportingClaimIds);
    }

    private static String versionWithStatus(
            String subject,
            List<String> aliases,
            PresetContractStatus status
    ) {
        return versionWithStatusAndAssociations(subject, aliases, status,
                List.of("project-a"), List.of(), List.of("claim-1"), List.of());
    }

    private static String versionWithDisplayAssociations(
            String subject,
            List<String> aliases,
            List<String> projectIds,
            List<String> caseIds
    ) {
        return versionWithStatusAndAssociations(subject, aliases, PresetContractStatus.ACTIVE,
                projectIds, caseIds, List.of("claim-1"), List.of());
    }

    private static String versionWithStatusAndAssociations(
            String subject,
            List<String> aliases,
            PresetContractStatus status,
            List<String> projectIds,
            List<String> caseIds,
            List<String> requiredClaimIds,
            List<String> supportingClaimIds
    ) {
        return PresetContractVersion.calculate(
                "preset-a",
                "Canonical?",
                aliases,
                subject,
                requiredClaimIds,
                supportingClaimIds,
                REQUIREMENT,
                status);
    }
}
