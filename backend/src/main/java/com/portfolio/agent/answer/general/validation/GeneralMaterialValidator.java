package com.portfolio.agent.answer.general.validation;

import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;
import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterialDraft;
import com.portfolio.agent.answer.general.domain.GeneralKnowledgeMetadata;
import com.portfolio.agent.answer.general.domain.GeneralStatement;
import com.portfolio.agent.answer.general.domain.GeneralSupportKind;
import com.portfolio.agent.answer.general.domain.MaterialCaveat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GeneralMaterialValidator {
    public GeneralMaterialValidationResult validate(GeneralAnswerMaterialDraft draft) {
        if (draft == null || blank(draft.getTopic()) || draft.getStatements().isEmpty()) return GeneralMaterialValidationResult.invalid("INVALID_GENERAL_MATERIAL");
        Set<String> aliases = new HashSet<>(); List<GeneralStatement> statements = new java.util.ArrayList<>();
        for (GeneralAnswerMaterialDraft.StatementDraft value : draft.getStatements()) {
            if (blank(value.getStatementAlias()) || blank(value.getText()) || value.getRole() == null
                    || value.getSupportKind() == null || !value.getPublicSourceKeys().isEmpty()
                    || !aliases.add(value.getStatementAlias())) return GeneralMaterialValidationResult.invalid("GENERAL_SOURCE_OR_ALIAS_INVALID");
            statements.add(new GeneralStatement(value.getStatementAlias(), value.getText(), value.getRole(), value.getConceptTags(), value.getSupportKind()));
        }
        List<MaterialCaveat> caveats = draft.getCaveats().stream().map(value -> {
            if (blank(value.getAlias()) || blank(value.getText())) throw new InvalidMaterialException();
            return new MaterialCaveat(value.getAlias(), value.getText());
        }).toList();
        if (draft.getMetadata() == null || blank(draft.getMetadata().getContentVersion())) return GeneralMaterialValidationResult.invalid("GENERAL_METADATA_MISSING");
        GeneralKnowledgeMetadata metadata = new GeneralKnowledgeMetadata(draft.getMetadata().getContentVersion(),
                draft.getMetadata().getAudienceRole(), draft.getMetadata().getDiscourseAliases());
        return GeneralMaterialValidationResult.valid(new GeneralAnswerMaterial(draft.getTopic(), statements, caveats, metadata));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static final class InvalidMaterialException extends RuntimeException { }
}
