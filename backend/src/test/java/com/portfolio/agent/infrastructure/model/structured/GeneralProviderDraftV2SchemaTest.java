package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralProviderDraftV2SchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"kind":"EXPLANATION","depth":"CONCISE",
             "definitionSentences":["这是定义"],
             "mechanismSentences":["这是机制"],"caveats":[]}
            """,
            """
            {"kind":"EXPLANATION","depth":"STANDARD",
             "definitionSentences":["这是定义","这是用途"],
             "mechanismSentences":["这是机制","这是边界"],
             "caveats":[{"kind":"RISK","sentences":["这是风险"]}]}
            """,
            """
            {"kind":"EXPLANATION","depth":"DETAILED",
             "definitionSentences":["定义一","定义二","定义三","定义四"],
             "mechanismSentences":["机制一","机制二","机制三","机制四"],
             "caveats":[]}
            """,
            """
            {"kind":"COMPARISON",
             "comparisonSentences":["甲的特点","乙的特点"],"caveats":[]}
            """
    })
    void acceptsClosedSentenceArrayShapes(String payload) {
        assertThat(validate(payload)).isEmpty();
    }

    @Test
    void acceptsOneOptionalChineseFullStopAtTheEndOfAnItem() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"CONCISE",
                 "definitionSentences":["这是定义。"],
                 "mechanismSentences":["这是机制"],"caveats":[]}
                """)).isEmpty();
    }

    @Test
    void rejectsScalarSentenceSerializationBecauseArraysOwnCardinality() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":"这是定义。这是用途。",
                 "mechanismSentences":"这是机制。这是边界",
                 "caveats":[{"kind":"RISK","sentences":"这是风险。"}]}
                """)).isNotEmpty();
        assertThat(validate("""
                {"kind":"COMPARISON",
                 "comparisonSentences":"甲的特点。乙的特点。","caveats":[]}
                """)).isNotEmpty();
    }

    @Test
    void rejectsMultipleSentencesInsideOneArrayItem() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["这是定义。这是用途。"],
                 "mechanismSentences":["这是机制。这是边界。"],
                 "caveats":[]}
                """)).isNotEmpty();
    }

    @Test
    void rejectsStandaloneTechnicalLabelInsideOneArrayItem() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"CONCISE",
                 "definitionSentences":["JSON Web Token（JWT）。它是一种令牌。"],
                 "mechanismSentences":["它用于传递声明。"],"caveats":[]}
                """)).isNotEmpty();
    }

    @Test
    void depthBranchesOwnProviderArrayCardinality() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"DETAILED",
                 "definitionSentences":["定义一","定义二","定义三"],
                 "mechanismSentences":["机制一","机制二","机制三","机制四"],
                 "caveats":[]}
                """)).isNotEmpty();
    }

    @Test
    void comparisonAllowsSemicolonAsClausePunctuation() {
        assertThat(validate("""
                {"kind":"COMPARISON",
                 "comparisonSentences":["甲适合一种场景；乙适合另一种场景"],
                 "caveats":[]}
                """)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"kind":"COMPARISON","depth":"STANDARD",
             "comparisonSentences":["比较内容"],"caveats":[]}
            """
    })
    void rejectsCrossBranchFields(String payload) {
        assertThat(validate(payload)).isNotEmpty();
    }

    @Test
    void rejectsForbiddenSentenceTerminators() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"CONCISE",
                 "definitionSentences":["这是定义。还有一句。"],
                 "mechanismSentences":["这是机制?"],"caveats":[]}
                """)).isNotEmpty();
    }

    @Test
    void rejectsMalformedScalarSentenceSequences() {
        assertThat(validate("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":"这是定义。。这是用途。",
                 "mechanismSentences":"这是机制。这是边界。","caveats":[]}
                """)).isNotEmpty();
        assertThat(validate("""
                {"kind":"COMPARISON",
                 "comparisonSentences":"甲的特点？乙的特点。","caveats":[]}
                """)).isNotEmpty();
    }

    private List<Error> validate(String payload) {
        try (InputStream input = getClass().getResourceAsStream(
                "/model-contracts/general.provider-draft.v2.schema.json")) {
            assertThat(input).as("General Draft v2 schema resource").isNotNull();
            JsonNode schemaNode = mapper.readTree(input);
            JsonNode instance = mapper.readTree(payload);
            Schema schema = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode);
            return schema.validate(instance);
        } catch (Exception failure) {
            throw new AssertionError("cannot load or validate General Draft v2", failure);
        }
    }
}
