package com.portfolio.agent.answer.general;

import com.portfolio.agent.answer.general.codec.GeneralAnswerMaterialDraftCodec;
import com.portfolio.agent.answer.general.codec.GeneralMaterialDecodingException;
import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;
import com.portfolio.agent.answer.general.render.DeterministicGeneralRenderer;
import com.portfolio.agent.answer.general.validation.GeneralMaterialValidationResult;
import com.portfolio.agent.answer.general.validation.GeneralMaterialValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralMaterialPipelineTest {
    private final GeneralAnswerMaterialDraftCodec codec = new GeneralAnswerMaterialDraftCodec();
    private final GeneralMaterialValidator validator = new GeneralMaterialValidator();

    @Test
    void validDraftBecomesMaterialAndDeterministicallyRendersSections() {
        GeneralMaterialValidationResult result = validator.validate(codec.decode("""
                {"schemaVersion":"general-material-v1","topic":"optimistic locking",
                 "statements":[{"statementAlias":"s1","text":"It rejects stale writes.","role":"MECHANISM",
                 "conceptTags":["concurrency"],"supportKind":"GENERAL_KNOWLEDGE","publicSourceKeys":[]}],
                 "caveats":[{"alias":"c1","text":"The exact behavior depends on the storage engine."}],
                 "metadata":{"contentVersion":"public-general-v1","audienceRole":"INTERVIEWER","discourseAliases":[]}}
                """));

        assertThat(result.isValid()).isTrue();
        GeneralAnswerMaterial material = result.getMaterial();
        assertThat(material.getStatements()).singleElement().satisfies(statement -> {
            assertThat(statement.getStatementAlias()).isEqualTo("s1");
            assertThat(statement.getSupportKind().name()).isEqualTo("GENERAL_KNOWLEDGE");
        });
        assertThat(new DeterministicGeneralRenderer().render(material).getSections()).hasSize(2);
    }

    @Test
    void codecAndValidatorRejectUnknownFieldsAndGeneralSources() {
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":\"general-material-v1\",\"topic\":\"x\",\"statements\":[],\"caveats\":[],\"metadata\":{},\"unexpected\":true}"))
                .isInstanceOf(GeneralMaterialDecodingException.class);
        GeneralMaterialValidationResult invalid = validator.validate(new com.portfolio.agent.answer.general.domain.GeneralAnswerMaterialDraft(
                "topic", java.util.List.of(new com.portfolio.agent.answer.general.domain.GeneralAnswerMaterialDraft.StatementDraft(
                        "s1", "text", com.portfolio.agent.answer.general.domain.GeneralStatementRole.DEFINITION,
                        java.util.Set.of(), com.portfolio.agent.answer.general.domain.GeneralSupportKind.GENERAL_KNOWLEDGE,
                        java.util.List.of("portfolio-source"))), java.util.List.of(),
                new com.portfolio.agent.answer.general.domain.GeneralAnswerMaterialDraft.MetadataDraft("v1", null, java.util.List.of())));
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.getFailureCode()).isEqualTo("GENERAL_SOURCE_OR_ALIAS_INVALID");
    }
}
