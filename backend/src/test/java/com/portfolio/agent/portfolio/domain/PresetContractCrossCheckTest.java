package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresetContractCrossCheckTest {

    @Test
    void sqlAuditOverviewContractVersionMatchesTheGovernanceProjection() {
        QuestionDefinition question = new QuestionDefinition(
                "sql-audit-overview",
                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？",
                List.of("请详细介绍SQL审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么", "详细介绍一下 SQL 审计与故障排查工具项目", "你在 SQL 审计与故障排查工具项目中做了什么", "你在SQL审计与故障排查工具项目中做了什么"),
                List.of("INTERVIEWER"),
                List.of("sql-audit-project"),
                List.of(),
                List.of("OVERVIEW"),
                List.of(ClaimCategory.BACKGROUND),
                List.of("HOME", "AGENT", "PROJECT"),
                true,
                10,
                "sql-audit-project",
                List.of("claim-sql-audit-background", "claim-sql-audit-responsibility", "claim-sql-audit-technical-decision", "claim-sql-audit-verification", "claim-sql-audit-delivered"),
                List.of("claim-sql-audit-documented-handoff"),
                new QuestionEvidenceRequirement(1, true),
                PresetContractStatus.ACTIVE);

        assertThat(question.getContractVersion()).isEqualTo("pcv1-52febb1bde125ab5");
        assertThat(PresetContractSetHash.calculate(List.of(question)))
                .isEqualTo("sha256:bd97cd9517e284fb2ce0227f536a90c32c836666b3a153068ac1792072472325");
    }
}
