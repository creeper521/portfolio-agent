package com.portfolio.agent.answer.composition.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.GroundedStatementContractTest;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementGroundingValidatorTest {
    @Test void acceptsGroundedRequiredSentenceAndRejectsAliasAndQualifierStrengthening() {
        ExpressionAliasRegistry aliases = aliases(PresentationRole.REQUIRED);
        FactExpressionDraft valid = draft("计划在 2026 年阶段性协作完成 API 原型 42%", "S001");
        assertThatCode(() -> new StatementGroundingValidator().validate(valid, aliases))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new StatementGroundingValidator()
                .validate(draft("已在 2026 年独立上线 API 生产版本 42%", "S001"), aliases))
                .isInstanceOf(GroundingValidationException.class);
        assertThatThrownBy(() -> new StatementGroundingValidator()
                .validate(draft("计划在 2026 年阶段性协作完成 API 原型 42%", "S999"), aliases))
                .isInstanceOf(GroundingValidationException.class);
    }

    @Test void contextCannotBeSoleSupport() {
        assertThatThrownBy(() -> new StatementGroundingValidator().validate(
                draft("计划在 2026 年阶段性协作完成 API 原型 42%", "S001"),
                aliases(PresentationRole.CONTEXT)))
                .isInstanceOf(GroundingValidationException.class);
    }

    private static FactExpressionDraft draft(String text, String alias) {
        return new FactExpressionDraft("portfolio-expression-draft.v1", null,
                List.of(new FactExpressionDraft.FactDraftSection(AnswerSectionType.VERIFICATION,
                        List.of(new DraftSentence(text, List.of(alias))))));
    }
    private static ExpressionAliasRegistry aliases(PresentationRole role) {
        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        com.portfolio.agent.answer.composition.domain.GroundedStatement statement =
                GroundedStatementContractTest.statement();
        aliases.addSubject("P01", statement.getSubjectReferences().get(0));
        aliases.addStatement("S001", new ExpressionStatement(statement, role,
                AnswerSectionType.VERIFICATION, 0));
        return aliases;
    }
}
