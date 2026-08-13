package com.portfolio.agent.answer.composition.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.domain.ControlledPredicate;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.StatementType;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SupportTarget;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class P4GroundingAdversarialDatasetTest {
    @Test void validatesVersionedAdversarialDataset() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/evaluation/p4/p4-grounding-adversarial-v1.json");
        List<Map<String, Object>> cases = new ObjectMapper().readValue(input,
                new TypeReference<List<Map<String, Object>>>() {});
        assertThat(cases).hasSizeGreaterThanOrEqualTo(10);
        StatementGroundingValidator validator = new StatementGroundingValidator();
        for (Map<String, Object> testCase : cases) {
            String source = (String) testCase.get("source");
            String draftText = (String) testCase.get("draft");
            boolean accepted = (Boolean) testCase.get("accepted");
            ExpressionAliasRegistry aliases = aliases(source,
                    (String) testCase.get("secondSubject"));
            FactExpressionDraft draft = new FactExpressionDraft(
                    "portfolio-expression-draft.v1", null,
                    List.of(new FactExpressionDraft.FactDraftSection(AnswerSectionType.SOLUTION,
                            List.of(new DraftSentence(draftText, List.of("S001"))))));
            boolean actual;
            try {
                validator.validate(draft, aliases);
                actual = true;
            } catch (GroundingValidationException exception) {
                actual = false;
            }
            assertThat(actual).as((String) testCase.get("name")).isEqualTo(accepted);
        }
    }

    private static ExpressionAliasRegistry aliases(String source, String secondSubject) {
        SubjectReference subject = new SubjectReference("公开项目");
        GroundedStatement statement = new GroundedStatement(StatementType.FACT, List.of(subject),
                ControlledPredicate.DESCRIBES, source, null, AnswerClaimCategory.IMPLEMENTATION,
                AnswerAchievementStatus.DELIVERED, AnswerContributionType.INDEPENDENT,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED, AnswerMateriality.KEY,
                SupportTarget.SUBJECT,
                List.of(new PublicSourceReferenceValue("REF-1", "来源", "v1", "CASE",
                        "/projects/public", "/evidence/public")));
        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        aliases.addSubject("P01", subject);
        if (secondSubject != null) aliases.addSubject("P02", new SubjectReference(secondSubject));
        aliases.addStatement("S001", new ExpressionStatement(statement, PresentationRole.REQUIRED,
                AnswerSectionType.SOLUTION, 0));
        return aliases;
    }
}
