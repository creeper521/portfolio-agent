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

class GoalProviderDraftV2SchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_FACT",
              "facets":["OVERVIEW"],"depth":"STANDARD"}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_COMPARE",
              "subjects":[
                {"kind":"PROJECT","reference":"alpha","inputText":"项目 alpha"},
                {"kind":"CASE","reference":"beta","inputText":"案例 beta"}],
              "dimensions":["ARCHITECTURE"]}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_RECOMMEND",
              "requestedSize":2,"constraints":[]}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"GENERAL_EXPLANATION",
              "topicText":"幂等性","depth":"DETAILED"}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"GENERAL_COMPARISON",
              "subjectTexts":["REST","RPC"],"dimensions":["ARCHITECTURE"]}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"APPLY_GENERAL_CONCEPT_TO_PORTFOLIO",
              "conceptText":"幂等性",
              "portfolioFacet":"SOLUTION","depth":"STANDARD"}}
            """
    })
    void acceptsEachCompleteGoalKindWithOnlyItsOwnRequiredFields(String payload) {
        assertThat(validate(payload)).isEmpty();
    }

    @Test
    void rejectsGoalLevelInputTextEchoBecauseServerDerivesTheAnchor() {
        assertThat(validate("""
                {"decision":"STANDARD_GOAL","goal":{
                  "goalKind":"PORTFOLIO_RECOMMEND",
                  "inputText":"推荐两个项目",
                  "requestedSize":2,"constraints":[]}}
                """)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"SUBJECT","prompt":"请指定项目","goal":{
                "goalKind":"PORTFOLIO_FACT",
                "facets":["OVERVIEW"],"depth":"STANDARD"}}}
            """,
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"SUBJECT","prompt":"还需要一个项目","goal":{
                "goalKind":"PORTFOLIO_COMPARE",
                "subjects":[
                  {"kind":"PROJECT","reference":"alpha","inputText":"项目 alpha"}],
                "dimensions":["ARCHITECTURE"]}}}
            """,
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"SUBJECT","prompt":"请选择两个项目","goal":{
                "goalKind":"PORTFOLIO_COMPARE",
                "dimensions":["ARCHITECTURE"]}}}
            """,
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"REQUESTED_SIZE","prompt":"需要几个项目","goal":{
                "goalKind":"PORTFOLIO_RECOMMEND",
                "constraints":[]}}}
            """
    })
    void acceptsOnlyTheThreeSupportedPartialGoalClarifications(String payload) {
        assertThat(validate(payload)).isEmpty();
    }

    @Test
    void acceptsDecisionOnlyClarificationForCandidateAndDiscussionModes() {
        assertThat(validate("{\"decision\":\"NEEDS_CLARIFICATION\"}"))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_FACT",
              "facets":[],"depth":"STANDARD"}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_COMPARE",
              "subjects":[
                {"kind":"PROJECT","reference":"alpha","inputText":"项目 alpha"}],
              "dimensions":["ARCHITECTURE"]}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_RECOMMEND",
              "requestedSize":2}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"GENERAL_EXPLANATION",
              "topicText":"幂等性"}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"GENERAL_COMPARISON",
              "subjectTexts":["REST","RPC"],"dimensions":[]}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"APPLY_GENERAL_CONCEPT_TO_PORTFOLIO",
              "conceptText":"幂等性",
              "portfolioFacet":"SOLUTION"}}
            """
    })
    void rejectsMissingOrEmptyRequiredFieldsForEachGoalKind(String payload) {
        assertThat(validate(payload)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_FACT",
              "facets":["OVERVIEW"],"depth":"STANDARD","requestedSize":2}}
            """,
            """
            {"decision":"STANDARD_GOAL","goal":{
              "goalKind":"PORTFOLIO_RECOMMEND",
              "requestedSize":2,"constraints":[],"subjects":[]}}
            """,
            """
            {"decision":"START_NEW_TOPIC","goal":{
              "goalKind":"GENERAL_EXPLANATION",
              "topicText":"幂等性","depth":"STANDARD"}}
            """,
            """
            {"decision":"CONVERSATIONAL","message":"你好","candidateKey":"C1"}
            """
    })
    void rejectsFieldsOwnedByAnotherGoalOrDecisionBranch(String payload) {
        assertThat(validate(payload)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"OUTPUT","prompt":"需要哪些内容","goal":{
                "goalKind":"PORTFOLIO_FACT",
                "depth":"STANDARD"}}}
            """,
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"SUBJECT","prompt":"请指定项目","goal":{
                "goalKind":"PORTFOLIO_COMPARE",
                "subjects":[
                  {"kind":"PROJECT","reference":"a","inputText":"项目 a"},
                  {"kind":"PROJECT","reference":"b","inputText":"项目 b"}],
                "dimensions":["ARCHITECTURE"]}}}
            """,
            """
            {"decision":"NEEDS_CLARIFICATION","clarification":{
              "field":"REQUESTED_SIZE","prompt":"需要几个项目","goal":{
                "goalKind":"PORTFOLIO_RECOMMEND",
                "requestedSize":2,"constraints":[]}}}
            """
    })
    void rejectsUnsupportedOrAlreadyResolvedClarificationShapes(String payload) {
        assertThat(validate(payload)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"decision\":\"CONVERSATIONAL\",\"message\":\"你好\"}",
            "{\"decision\":\"ENTER_RECOMMENDED_RESULT\",\"candidateKey\":\"C1\"}",
            "{\"decision\":\"SWITCH_PROJECT\",\"candidateKey\":\"C2\"}",
            "{\"decision\":\"START_NEW_TOPIC\"}",
            "{\"decision\":\"REENTER_PROJECT\"}"
    })
    void acceptsNonGoalDecisionShapes(String payload) {
        assertThat(validate(payload)).isEmpty();
    }

    private List<Error> validate(String payload) {
        try (InputStream input = getClass().getResourceAsStream(
                "/model-contracts/goal.provider-draft.v2.schema.json")) {
            assertThat(input).as("v2 schema resource").isNotNull();
            JsonNode schemaNode = mapper.readTree(input);
            JsonNode instance = mapper.readTree(payload);
            Schema schema = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode);
            return schema.validate(instance);
        } catch (Exception failure) {
            throw new AssertionError("cannot load or validate v2 schema", failure);
        }
    }

    @Test
    void acceptsExplicitNullSiblingsForUnselectedDecisionFields() {
        assertThat(validate("""
                {"decision":"STANDARD_GOAL",
                 "goal":{"goalKind":"PORTFOLIO_RECOMMEND",
                   "requestedSize":2,"constraints":[]},
                 "message":null,"candidateKey":null,
                 "clarification":null,"recentReference":null}
                """)).isEmpty();
        assertThat(validate("""
                {"decision":"CONVERSATIONAL","message":"你好",
                 "candidateKey":null,"goal":null,
                 "clarification":null,"recentReference":null}
                """)).isEmpty();
        assertThat(validate(
                "{\"decision\":\"NEEDS_CLARIFICATION\",\"message\":null," +
                        "\"candidateKey\":null,\"goal\":null," +
                        "\"clarification\":null,\"recentReference\":null}"))
                .isEmpty();
    }

    @Test
    void stillRejectsRealValuesForFieldsOwnedByAnotherBranch() {
        assertThat(validate("""
                {"decision":"CONVERSATIONAL","message":"你好",
                 "clarification":{"field":"SUBJECT","prompt":"请指定项目",
                   "goal":{"goalKind":"PORTFOLIO_FACT",
                     "facets":["OVERVIEW"],"depth":"STANDARD"}}}
                """)).isNotEmpty();
        assertThat(validate("""
                {"decision":"ENTER_RECOMMENDED_RESULT","candidateKey":null}
                """)).isNotEmpty();
    }
}
