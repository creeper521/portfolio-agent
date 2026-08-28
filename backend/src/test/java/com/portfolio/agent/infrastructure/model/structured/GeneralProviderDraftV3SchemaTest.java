package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralProviderDraftV3SchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"kind":"EXPLANATION","depth":"CONCISE",
             "definitionSentences":["\u8fd9\u662f\u5b9a\u4e49"],
             "mechanismSentences":["\u8fd9\u662f\u673a\u5236"],"caveats":[]}
            """,
            """
            {"kind":"COMPARISON","comparisonSentences":[
              {"text":"\u4e59\u7684\u673a\u5236\u7279\u5f81","dimension":"MECHANISM","subjectIndex":2},
              {"text":"\u7532\u7684\u53d6\u820d\u7279\u5f81","dimension":"TRADE_OFF","subjectIndex":1}],
             "caveats":[]}
            """
    })
    void acceptsClosedV3Shapes(String payload) {
        assertThat(validate(payload)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"kind":"COMPARISON","comparisonSentences":["\u7532\u7684\u7279\u5f81"],"caveats":[]}
            """,
            """
            {"kind":"COMPARISON","comparisonSentences":[
              {"text":"\u7532\u7684\u7279\u5f81","subjectIndex":1}],"caveats":[]}
            """,
            """
            {"kind":"COMPARISON","comparisonSentences":[
              {"text":"\u7532\u7684\u7279\u5f81","dimension":"lower-case","subjectIndex":1}],"caveats":[]}
            """,
            """
            {"kind":"COMPARISON","comparisonSentences":[
              {"text":"\u7532\u7684\u7279\u5f81","dimension":"MECHANISM","subjectIndex":0}],"caveats":[]}
            """,
            """
            {"kind":"COMPARISON","comparisonSentences":[
              {"text":"\u7532\u7684\u7279\u5f81","dimension":"MECHANISM","subjectIndex":6}],"caveats":[]}
            """,
            """
            {"kind":"COMPARISON","comparisonSentences":[
              {"text":"\u7532\u7684\u7279\u5f81","dimension":"MECHANISM","subjectIndex":1,
               "subject":"\u7532"}],"caveats":[]}
            """
    })
    void rejectsMalformedOrOpenComparisonItems(String payload) {
        assertThat(validate(payload)).isNotEmpty();
    }

    private List<Error> validate(String payload) {
        try (InputStream input = getClass().getResourceAsStream(
                "/model-contracts/general.provider-draft.v3.schema.json")) {
            assertThat(input).as("General Draft v3 schema resource").isNotNull();
            JsonNode schemaNode = mapper.readTree(input);
            JsonNode instance = mapper.readTree(payload);
            Schema schema = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode);
            return schema.validate(instance);
        } catch (Exception failure) {
            throw new AssertionError(
                    "cannot load or validate General Draft v3", failure);
        }
    }
}
