package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioModelContractTest {

    @Test
    void questionDefinitionDefensivelyCopiesAliasesAndKeepsValueSemantics() {
        List<String> aliases = new ArrayList<>(List.of("介绍项目"));
        QuestionDefinition first = new QuestionDefinition(
                "question-1", "完整介绍", aliases, List.of("INTERVIEWER"),
                List.of("project-1"), List.of("OVERVIEW"), List.of(ClaimCategory.OUTCOME),
                List.of("HOME"), true, 10
        );
        QuestionDefinition second = new QuestionDefinition(
                "question-1", "完整介绍", List.of("介绍项目"), List.of("INTERVIEWER"),
                List.of("project-1"), List.of("OVERVIEW"), List.of(ClaimCategory.OUTCOME),
                List.of("HOME"), true, 10
        );

        aliases.add("后来加入的别名");

        assertThat(first.getAliases()).containsExactly("介绍项目");
        assertThatThrownBy(() -> first.getAliases().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).contains("question-1", "完整介绍");
    }

    @Test
    void projectProfileDefensivelyCopiesEveryCollection() {
        List<String> responsibilities = new ArrayList<>(List.of("负责后端"));
        List<String> keyDecisions = new ArrayList<>(List.of("使用端口隔离"));
        List<String> technologies = new ArrayList<>(List.of("Spring Boot"));
        List<String> verification = new ArrayList<>(List.of("单元测试"));
        List<String> claimIds = new ArrayList<>(List.of("claim-1"));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
        List<String> timelineEventIds = new ArrayList<>(List.of("timeline-1"));

        ProjectProfile profile = new ProjectProfile(
                "project-1",
                "P-01",
                "project-one",
                "项目一",
                "项目摘要",
                "项目背景",
                responsibilities,
                "解决方案",
                keyDecisions,
                technologies,
                verification,
                "项目结果",
                "交接说明",
                ProjectStatus.DELIVERED,
                ContributionType.PRIMARY,
                claimIds,
                evidenceIds,
                timelineEventIds
        );

        responsibilities.add("外部新增职责");
        keyDecisions.add("外部新增决策");
        technologies.add("外部新增技术");
        verification.add("外部新增验证");
        claimIds.add("claim-2");
        evidenceIds.add("evidence-2");
        timelineEventIds.add("timeline-2");

        assertThat(profile.getResponsibilities()).containsExactly("负责后端");
        assertThat(profile.getKeyDecisions()).containsExactly("使用端口隔离");
        assertThat(profile.getTechnologies()).containsExactly("Spring Boot");
        assertThat(profile.getVerification()).containsExactly("单元测试");
        assertThat(profile.getClaimIds()).containsExactly("claim-1");
        assertThat(profile.getEvidenceIds()).containsExactly("evidence-1");
        assertThat(profile.getTimelineEventIds()).containsExactly("timeline-1");
        assertThatThrownBy(() -> profile.getResponsibilities().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getKeyDecisions().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getTechnologies().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getVerification().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getClaimIds().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getEvidenceIds().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void timelineEventDefensivelyCopiesReferencesAndKeepsValueSemantics() {
        List<String> projectIds = new ArrayList<>(List.of("project-1"));
        List<String> claimIds = new ArrayList<>(List.of("claim-1"));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
        TimelineEvent first = new TimelineEvent(
                "timeline-1", "2026.06–07", "交付闭环", "路径硬编码",
                "完成多目标路由", "形成可交付版本", projectIds, claimIds, evidenceIds
        );
        TimelineEvent second = new TimelineEvent(
                "timeline-1", "2026.06–07", "交付闭环", "路径硬编码",
                "完成多目标路由", "形成可交付版本",
                List.of("project-1"), List.of("claim-1"), List.of("evidence-1")
        );

        projectIds.add("private-project");
        evidenceIds.add("private-evidence");

        assertThat(first.getProjectIds()).containsExactly("project-1");
        assertThat(first.getEvidenceIds()).containsExactly("evidence-1");
        assertThatThrownBy(() -> first.getProjectIds().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).contains("timeline-1", "交付闭环");
    }

    @Test
    void snapshotDefensivelyCopiesTimeline() {
        List<TimelineEvent> timeline = new ArrayList<>(List.of(new TimelineEvent(
                "timeline-1", "2026.06–07", "交付闭环", "问题", "行动", "影响",
                List.of("project-1"), List.of("claim-1"), List.of("evidence-1")
        )));
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                "1.0", "version-1", OffsetDateTime.parse("2026-07-17T00:00:00+08:00"),
                null, List.of(), List.of(), List.of(), List.of(), List.of(), timeline
        );

        timeline.clear();

        assertThat(snapshot.getTimeline()).hasSize(1);
        assertThatThrownBy(() -> snapshot.getTimeline().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
