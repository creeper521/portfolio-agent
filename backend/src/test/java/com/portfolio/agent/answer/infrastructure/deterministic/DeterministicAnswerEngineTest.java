package com.portfolio.agent.answer.infrastructure.deterministic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.application.QuestionNormalizer;
import com.portfolio.agent.answer.domain.model.AnswerMode;
import com.portfolio.agent.answer.domain.model.AnswerResult;
import com.portfolio.agent.answer.domain.model.AnswerSection;
import com.portfolio.agent.answer.domain.model.AnswerSectionType;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnswerEngineTest {

    private DeterministicAnswerEngine engine;

    @BeforeEach
    void setUp() {
        JsonPublicPortfolioRepository repository = new JsonPublicPortfolioRepository(
                new ObjectMapper().findAndRegisterModules(),
                new ClassPathResource("public-data/public-portfolio.v1.json"),
                new PortfolioSnapshotValidator()
        );
        engine = new DeterministicAnswerEngine(repository, new QuestionNormalizer());
    }

    @Test
    void matchesCanonicalQuestionAndBuildsFiveGroundedSections() {
        AnswerResult result = engine.answer("sql-audit",
                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？");

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
        assertThat(result.getEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.getTitle()).isEqualTo("SQL 审计工具交付证据集");
            assertThat(evidence.getRawContentPublic()).isFalse();
        });
    }

    @Test
    void matchesAnExplicitlyReviewedAlias() {
        AnswerResult result = engine.answer("sql-audit", "  你在SQL审计与故障排查工具项目中做了什么？ ");

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    void doesNotGuessForUnsupportedQuestion() {
        AnswerResult result = engine.answer("sql-audit", "这个项目最大的性能提升是多少？");

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getSections()).extracting(AnswerSection::getType)
                .containsExactly(AnswerSectionType.BOUNDARY);
        assertThat(result.getEvidence()).isEmpty();
        assertThat(result.getSuggestedQuestions()).isEqualTo(List.of(
                "请详细介绍 SQL 审计与故障排查工具项目：背景、我的职责、技术方案、验证过程和最终状态分别是什么？"
        ));
    }

    @Test
    void returnsNoMatchForUnknownProjectWithoutLeakingOtherContent() {
        AnswerResult result = engine.answer("missing-project", "请详细介绍 SQL 审计与故障排查工具项目");

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getEvidence()).isEmpty();
        assertThat(result.getSuggestedQuestions()).isEmpty();
    }
}
