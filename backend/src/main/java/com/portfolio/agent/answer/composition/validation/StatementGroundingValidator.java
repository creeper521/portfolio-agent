package com.portfolio.agent.answer.composition.validation;

import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.DraftText;
import com.portfolio.agent.answer.composition.domain.draft.ModelExpressionDraft;
import com.portfolio.agent.answer.composition.projection.ExpressionAliasRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StatementGroundingValidator {
    private final ProtectedAtomExtractor atomExtractor = new ProtectedAtomExtractor();
    private final QualifierPreservationValidator qualifierValidator = new QualifierPreservationValidator();

    public void validate(ModelExpressionDraft draft, ExpressionAliasRegistry aliases) {
        if (draft == null || aliases == null || draft.allBodySentences().isEmpty()) {
            reject(GroundingValidationException.Code.STRUCTURE);
        }
        Set<String> covered = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (DraftText intro : draft.introductoryTexts()) {
            validateBinding(intro.getText(), intro.getSupports(), aliases, false, covered, texts);
        }
        for (DraftSentence sentence : draft.allBodySentences()) {
            validateBinding(sentence.getText(), sentence.getSupports(), aliases, true, covered, texts);
        }
        for (String alias : aliases.statementAliases()) {
            ExpressionStatement entry = aliases.expressionStatement(alias);
            if (entry.getPresentationRole() == PresentationRole.REQUIRED && !covered.contains(alias)) {
                reject(GroundingValidationException.Code.REQUIRED_COVERAGE);
            }
        }
    }

    private void validateBinding(String text, List<String> supportAliases,
            ExpressionAliasRegistry aliases, boolean countsCoverage, Set<String> covered,
            Set<String> texts) {
        if (!texts.add(text)) reject(GroundingValidationException.Code.STRUCTURE);
        List<ExpressionStatement> entries = supportAliases.stream().map(alias -> {
            ExpressionStatement entry = aliases.expressionStatement(alias);
            if (entry == null) reject(GroundingValidationException.Code.ALIAS_SCOPE);
            return entry;
        }).toList();
        if (entries.stream().allMatch(entry -> entry.getPresentationRole() == PresentationRole.CONTEXT)) {
            reject(GroundingValidationException.Code.ALIAS_SCOPE);
        }
        if (countsCoverage) covered.addAll(supportAliases);
        List<GroundedStatement> statements = entries.stream()
                .map(ExpressionStatement::getStatement).toList();
        if (!atomExtractor.isSubsetOfSupportedAtoms(text, statements)
                || !atomExtractor.preservesSupportedAtoms(text, statements)) {
            reject(GroundingValidationException.Code.PROTECTED_ATOM);
        }
        Set<String> allSubjects = new HashSet<>();
        aliases.subjectAliases().forEach(alias -> allSubjects.add(aliases.subject(alias).getPublicLabel()));
        if (!atomExtractor.containsOnlyKnownSubjects(text, statements, allSubjects)) {
            reject(GroundingValidationException.Code.ALIAS_SCOPE);
        }
        if (!qualifierValidator.isPreserved(text, statements)) {
            reject(GroundingValidationException.Code.QUALIFIER);
        }
    }

    private static void reject(GroundingValidationException.Code code) {
        throw new GroundingValidationException(code);
    }
}
