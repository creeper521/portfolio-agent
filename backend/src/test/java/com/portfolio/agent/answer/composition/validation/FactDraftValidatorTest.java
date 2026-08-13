package com.portfolio.agent.answer.composition.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.RequestedFacet;
import com.portfolio.agent.answer.composition.domain.RequestedOutput;
import com.portfolio.agent.answer.composition.domain.ResponseDepth;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactDraftValidatorTest {
    @Test void authoritativeMaterialFixesSectionIdentityAndOrder() {
        GroundedStatement backgroundStatement = ProtectedAtomExtractorTest.statement("协作公开背景");
        GroundedStatement verificationStatement = ProtectedAtomExtractorTest.statement("协作公开验证");
        ExpressionStatement background = new ExpressionStatement(backgroundStatement,
                PresentationRole.REQUIRED, AnswerSectionType.BACKGROUND, 0);
        ExpressionStatement verification = new ExpressionStatement(verificationStatement,
                PresentationRole.REQUIRED, AnswerSectionType.VERIFICATION, 0);
        SubjectReference subject = backgroundStatement.getSubjectReferences().get(0);
        FactAnswerMaterial material = new FactAnswerMaterial("服务端标题", subject, FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.BACKGROUND,
                                List.of(background), OrderingPolicy.STABLE),
                        new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                                List.of(verification), OrderingPolicy.STABLE)),
                SummaryPolicy.FORBIDDEN, List.of(), List.of());
        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        aliases.addSubject("P01", subject);
        aliases.addStatement("S001", background);
        aliases.addStatement("S002", verification);
        FactExpressionDraft ordered = draft(false);
        FactExpressionDraft reordered = draft(true);
        FactDraftValidator validator = new FactDraftValidator();
        assertThatCode(() -> validator.validate(ordered, material, aliases, context(subject)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(reordered, material, aliases, context(subject)))
                .isInstanceOf(GroundingValidationException.class)
                .extracting(exception -> ((GroundingValidationException) exception).getCode())
                .isEqualTo(GroundingValidationException.Code.TASK_SCOPE);
    }

    private static FactExpressionDraft draft(boolean reversed) {
        FactExpressionDraft.FactDraftSection background = new FactExpressionDraft.FactDraftSection(
                AnswerSectionType.BACKGROUND,
                List.of(new DraftSentence("协作公开背景", List.of("S001"))));
        FactExpressionDraft.FactDraftSection verification = new FactExpressionDraft.FactDraftSection(
                AnswerSectionType.VERIFICATION,
                List.of(new DraftSentence("协作公开验证", List.of("S002"))));
        return new FactExpressionDraft("portfolio-expression-draft.v1", null,
                reversed ? List.of(verification, background) : List.of(background, verification));
    }

    private static PortfolioCompositionContext context(SubjectReference subject) {
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.BACKGROUND, RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of(subject.getPublicLabel()));
        return new PortfolioCompositionContext(intent,
                new ExpressionAllowance(true, Instant.now().plusSeconds(5), 1800, 16, 1));
    }
}
