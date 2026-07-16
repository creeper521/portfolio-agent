package com.portfolio.agent.answer.engine.deterministic;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.AnswerSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.service.QuestionNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnswerEngineTest {

    private DeterministicAnswerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeterministicAnswerEngine(new QuestionNormalizer());
    }

    @Test
    void matchesCanonicalQuestionAndBuildsFiveGroundedSections() {
        AnswerResult result = engine.answer(
                knowledge(),
                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？"
        );

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getAnswerMode()).isEqualTo(AnswerMode.DETERMINISTIC);
        assertThat(result.isFallback()).isFalse();
        assertThat(result.getSections()).extracting(AnswerSection::getType)
                .containsExactly(
                        AnswerSectionType.BACKGROUND,
                        AnswerSectionType.RESPONSIBILITY,
                        AnswerSectionType.SOLUTION,
                        AnswerSectionType.VERIFICATION,
                        AnswerSectionType.STATUS
                );
        assertThat(result.getSections()).allSatisfy(
                section -> assertThat(section.getContent()).isNotBlank());
        assertThat(result.getSections()).extracting(AnswerSection::getContent)
                .containsExactly(
                        "项目背景",
                        "职责一。 职责二。",
                        "技术方案。 关键决策包括：决策一。 决策二。",
                        "验证一。 验证二。",
                        "最终结果。 交接状态。"
                );
        assertThat(result.getEvidence()).extracting(AnswerEvidence::getId)
                .containsExactly("evidence-2", "evidence-1");
        assertThat(result.getSuggestedQuestions())
                .containsExactly("完整介绍建议", "职责建议");
    }

    @Test
    void matchesAnExplicitlyReviewedAlias() {
        AnswerResult result = engine.answer(
                knowledge(),
                "  你在SQL审计与故障排查工具项目中做了什么？ "
        );

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    void doesNotGuessForUnsupportedQuestion() {
        AnswerResult result = engine.answer(knowledge(), "这个项目最大的性能提升是多少？");

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getSections()).extracting(AnswerSection::getType)
                .containsExactly(AnswerSectionType.BOUNDARY);
        assertThat(result.getEvidence()).isEmpty();
        assertThat(result.getSuggestedQuestions())
                .containsExactly("完整介绍建议", "职责建议");
    }

    private AnswerKnowledge knowledge() {
        return new AnswerKnowledge(
                "sql-audit",
                "SQL 审计与故障排查工具",
                "项目背景",
                List.of("职责一。", "职责二。"),
                "技术方案。",
                List.of("决策一。", "决策二。"),
                List.of("验证一。", "验证二。"),
                "最终结果。",
                "交接状态。",
                "DELIVERED",
                List.of(
                        new AnswerQuestion(
                                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？",
                                List.of("你在SQL审计与故障排查工具项目中做了什么？"),
                                "完整介绍建议"
                        ),
                        new AnswerQuestion(
                                "你承担了哪些职责？",
                                List.of("你的职责是什么？"),
                                "职责建议"
                        )
                ),
                List.of(evidence("evidence-2"), evidence("evidence-1"))
        );
    }

    private AnswerEvidence evidence(String id) {
        return new AnswerEvidence(
                id,
                "证据 " + id,
                "DOCUMENT",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 14),
                2,
                "摘要 " + id,
                List.of("事实 " + id),
                "APPROVED",
                false
        );
    }
}
