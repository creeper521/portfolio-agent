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
                "question-1", "project-1", "完整介绍", aliases, "介绍项目"
        );
        QuestionDefinition second = new QuestionDefinition(
                "question-1", "project-1", "完整介绍", List.of("介绍项目"), "介绍项目"
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
        List<String> questionIds = new ArrayList<>(List.of("question-1"));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));

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
                questionIds,
                evidenceIds
        );

        responsibilities.add("外部新增职责");
        keyDecisions.add("外部新增决策");
        technologies.add("外部新增技术");
        verification.add("外部新增验证");
        questionIds.add("question-2");
        evidenceIds.add("evidence-2");

        assertThat(profile.getResponsibilities()).containsExactly("负责后端");
        assertThat(profile.getKeyDecisions()).containsExactly("使用端口隔离");
        assertThat(profile.getTechnologies()).containsExactly("Spring Boot");
        assertThat(profile.getVerification()).containsExactly("单元测试");
        assertThat(profile.getQuestionIds()).containsExactly("question-1");
        assertThat(profile.getEvidenceIds()).containsExactly("evidence-1");
        assertThatThrownBy(() -> profile.getResponsibilities().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getKeyDecisions().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getTechnologies().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getVerification().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getQuestionIds().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.getEvidenceIds().add("禁止修改"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void timelineEventDefensivelyCopiesReferencesAndKeepsValueSemantics() {
        List<String> projectSlugs = new ArrayList<>(List.of("sql-audit"));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
        TimelineEvent first = new TimelineEvent(
                "timeline-1", "2026.06–07", "交付闭环", "路径硬编码",
                "完成多目标路由", "形成可交付版本", projectSlugs, evidenceIds
        );
        TimelineEvent second = new TimelineEvent(
                "timeline-1", "2026.06–07", "交付闭环", "路径硬编码",
                "完成多目标路由", "形成可交付版本",
                List.of("sql-audit"), List.of("evidence-1")
        );

        projectSlugs.add("private-project");
        evidenceIds.add("private-evidence");

        assertThat(first.getProjectSlugs()).containsExactly("sql-audit");
        assertThat(first.getEvidenceIds()).containsExactly("evidence-1");
        assertThatThrownBy(() -> first.getProjectSlugs().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).contains("timeline-1", "交付闭环");
    }

    @Test
    void snapshotDefensivelyCopiesTimeline() {
        List<TimelineEvent> timeline = new ArrayList<>(List.of(new TimelineEvent(
                "timeline-1", "2026.06–07", "交付闭环", "问题", "行动", "影响",
                List.of("sql-audit"), List.of("evidence-1")
        )));
        PortfolioSnapshot snapshot = new PortfolioSnapshot(
                "1.0", "version-1", OffsetDateTime.parse("2026-07-17T00:00:00+08:00"),
                null, List.of(), List.of(), List.of(), timeline
        );

        timeline.clear();

        assertThat(snapshot.getTimeline()).hasSize(1);
        assertThatThrownBy(() -> snapshot.getTimeline().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
