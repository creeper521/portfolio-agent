package com.portfolio.agent.infrastructure.model.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputContractRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void contractRefProjectsAStableOperationIdWithoutLeakingPolicyType() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v3");

        assertThat(ref.operationId()).isEqualTo("GENERAL_KNOWLEDGE");
    }

    @Test
    void onlyTheStructuredPackageCanConstructValidatedOutputCapabilities() {
        assertThat(StructurallyValidatedOutput.class.isRecord()).isFalse();
        assertThat(Modifier.isFinal(
                StructurallyValidatedOutput.class.getModifiers())).isTrue();
        assertThat(StructurallyValidatedOutput.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(
                        Modifier.isPublic(constructor.getModifiers())).isFalse());
    }

    private final StructuredOutputContractRegistry registry =
            StructuredOutputContractRegistry.standard();

    @Test
    void resolvesGeneralV4ProviderAndV3CanonicalContracts() {
        StructuredOutputContract provider = registry.resolve(
                new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v4"));
        StructuredOutputContract canonical = registry.resolve(
                new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                        "general.draft.v3"));

        assertThat(provider.canonicalSchema().path("$id").textValue())
                .isEqualTo("urn:portfolio-agent:model-contract:general.provider-draft.v4");
        assertThat(canonical.canonicalSchema().path("$id").textValue())
                .isEqualTo("urn:portfolio-agent:model-contract:general.draft.v3");
        assertThat(provider.contractFingerprint())
                .isNotEqualTo(canonical.contractFingerprint());
    }

    @Test
    void resolvesClosedOperationContractAndProducesStableFingerprint() {
        StructuredOutputContract contract = registry.resolve(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"));

        assertThat(contract.outputName()).isEqualTo("general_draft");
        assertThat(contract.contractFingerprint()).hasSize(64);
        assertThat(contract.canonicalSchema().path("$schema").textValue())
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
    }

    @Test
    void resolvesProviderDraftAndCanonicalContractsForTheSameOperation() {
        StructuredOutputContract legacyProvider = registry.resolve(
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION,
                        "goal.provider-draft.v1"));
        StructuredOutputContract provider = registry.resolve(
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION,
                        "goal.provider-draft.v2"));
        StructuredOutputContract canonical = registry.resolve(
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION,
                        "goal.proposal.v5"));

        assertThat(legacyProvider.outputName()).isEqualTo("goal_provider_draft");
        assertThat(provider.outputName()).isEqualTo("goal_provider_draft_v2");
        assertThat(provider.contractFingerprint())
                .isNotEqualTo(canonical.contractFingerprint());
    }

    @Test
    void generalProviderDraftReportsItsOwnSchemaReasonInsteadOfGoalRootKind() {
        assertThatThrownBy(() -> registry.validate(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v2"),
                """
                {"kind":"EXPLANATION","depth":"CONCISE",
                 "definitionSentences":["这是定义?"],
                 "mechanismSentences":["这是机制"],"caveats":[]}
                """))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason
                        .STRING_CONSTRAINT_INVALID);
    }

    @Test
    void goalSpecificRootFailuresReceiveOnlyGenericRegistryClassification() {
        StructuredOutputValidationException failure =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> registry.validate(
                                new StructuredContractRef(
                                        ModelOperation.TURN_INTERPRETATION,
                                        "goal.provider-draft.v2"),
                                "{\"kind\":\"PORTFOLIO_RECOMMEND\"}"),
                        StructuredOutputValidationException.class);

        assertThat(failure.getReason()).isNotIn(
                StructuredOutputValidationException.Reason
                        .UNSUPPORTED_ROOT_KIND,
                StructuredOutputValidationException.Reason
                        .CLARIFICATION_BLOCKED_GOAL_REQUIRED);
        assertThat(failure.getStage()).isEqualTo(
                StructuredOutputValidationException.Stage.UNCLASSIFIED_SCHEMA);
    }

    @Test
    void explanationArrayReportsTheActualSafePunctuationConstraint() {
        assertThatThrownBy(() -> registry.validate(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v2"),
                """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["这是定义；这是用途","这是典型用途"],
                 "mechanismSentences":["这是机制","这是边界"],"caveats":[]}
                """))
                .isInstanceOf(StructuredOutputValidationException.class)
                .satisfies(failure -> {
                    StructuredOutputValidationException typed =
                            (StructuredOutputValidationException) failure;
                    assertThat(typed.getReason()).isEqualTo(
                            StructuredOutputValidationException.Reason
                                    .STRING_CONSTRAINT_INVALID);
                    assertThat(typed.getDiagnosticReason()).isEqualTo(
                            "STRING_CONSTRAINT_INVALID_DEFINITION_SENTENCES_SEMICOLON");
                });
    }

    @Test
    void sentenceArrayReportsTheActualSafePunctuationConstraint() {
        assertThatThrownBy(() -> registry.validate(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v2"),
                """
                {"kind":"EXPLANATION","depth":"CONCISE",
                 "definitionSentences":["这是定义；还有说明"],
                 "mechanismSentences":["这是机制"],"caveats":[]}
                """))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo(
                        "STRING_CONSTRAINT_INVALID_DEFINITION_SENTENCES_SEMICOLON");
    }


    @Test
    void returnedSchemaAndValidatedTreeCannotMutateRegistryAuthority() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2");
        StructuredOutputContract contract = registry.resolve(ref);
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                contract.canonicalSchema()).removeAll();
        StructurallyValidatedOutput output = registry.validate(ref, """
                {"topic":"幂等性","statements":[{
                  "role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]
                }],"caveats":[]}
                """);
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                output.jsonTree()).removeAll();

        assertThat(registry.resolve(ref).canonicalSchema().path("$id").textValue())
                .isEqualTo("urn:portfolio-agent:model-contract:general.draft.v2");
        assertThat(output.jsonTree().path("topic").textValue()).isEqualTo("幂等性");
    }

    @Test
    void validatesGeneralDraftAndReturnsTheValidatedTree() {
        StructurallyValidatedOutput output = registry.validate(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"),
                """
                {
                  "topic": "幂等性",
                  "statements": [{
                    "role": "DEFINITION",
                    "text": "同一请求重复执行仍保持同一业务效果。",
                    "subject": null,
                    "dimension": null,
                    "aspects": ["DEFINITION"]
                  }],
                  "caveats": []
                }
                """);

        assertThat(output.contractRef().schemaVersion()).isEqualTo("general.draft.v2");
        assertThat(output.jsonTree().path("topic").textValue()).isEqualTo("幂等性");
    }

    @Test
    void rejectsUnknownFieldsAsLocalSchemaFailure() {
        assertThatThrownBy(() -> registry.validate(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"),
                """
                {
                  "topic": "幂等性",
                  "statements": [{"role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]}],
                  "caveats": [],
                  "unexpected": true
                }
                """))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.UNKNOWN_FIELD);
    }

    @Test
    void rejectsDuplicateKeysAndTrailingTokensBeforeSchemaValidation() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2");

        assertThatThrownBy(() -> registry.validate(
                ref, "{\"topic\":\"a\",\"topic\":\"b\",\"statements\":[],\"caveats\":[]}"))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.INVALID_JSON);
        assertThatThrownBy(() -> registry.validate(
                ref, "{\"topic\":\"a\",\"statements\":[],\"caveats\":[]} {}"))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.INVALID_JSON);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "STANDARD_GOAL", "ENTER_RECOMMENDED_RESULT",
            "CONTINUE_CURRENT_PROJECT", "START_NEW_TOPIC",
            "SWITCH_PROJECT", "REENTER_PROJECT", "NEEDS_CLARIFICATION"
    })
    void canonicalGoalContractAcceptsEveryClosedSemanticRoute(String route) {
        StructurallyValidatedOutput output = registry.validate(
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION, "goal.proposal.v5"),
                """
                {
                  "kind":"SEMANTIC_ROUTE",
                  "route":"%s",
                  "candidateKey":null,
                  "goal":null,
                  "clarification":null,
                  "recentReference":null
                }
                """.formatted(route));

        assertThat(output.jsonTree().path("route").textValue()).isEqualTo(route);
    }

    @Test
    void canonicalGoalContractAcceptsConversationalRecentReferenceAndBlockedGoalShapes() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.TURN_INTERPRETATION, "goal.proposal.v5");

        assertThat(registry.validate(ref,
                "{\"kind\":\"CONVERSATIONAL\",\"message\":\"请说明目标\"}"))
                .isNotNull();
        assertThat(registry.validate(ref, """
                {
                  "kind":"SEMANTIC_ROUTE","route":"STANDARD_GOAL",
                  "candidateKey":null,"goal":null,"clarification":null,
                  "recentReference":{"goalId":"goal-1","sectionId":"section-1"}
                }
                """)).isNotNull();
        assertThat(registry.validate(ref, """
                {
                  "kind":"SEMANTIC_ROUTE","route":"NEEDS_CLARIFICATION",
                  "candidateKey":null,"goal":null,"recentReference":null,
                  "clarification":{
                    "field":"REQUESTED_SIZE","prompt":"需要推荐几个项目？",
                    "blockedGoal":{
                      "goalKind":"PORTFOLIO_RECOMMEND","subjects":[],
                      "requestedOutputs":["RECOMMENDATION"],"facets":[],
                      "dimensions":[],"requestedSize":null,"constraints":[],
                      "portfolioDepth":"STANDARD",
                      "unresolvedField":"REQUESTED_SIZE",
                      "askedFields":["REQUESTED_SIZE"],"remainingFields":[],
                      "depth":1
                    }
                  }
                }
                """)).isNotNull();
    }

    @Test
    void canonicalGoalContractRejectsRecommendationSizeOutsideDomainBoundary() {
        assertThatThrownBy(() -> registry.validate(
                new StructuredContractRef(
                        ModelOperation.TURN_INTERPRETATION, "goal.proposal.v5"),
                """
                {
                  "kind":"SEMANTIC_ROUTE","route":"STANDARD_GOAL",
                  "candidateKey":null,"clarification":null,"recentReference":null,
                  "goal":{
                    "goalKey":"recommend","goalKind":"PORTFOLIO_RECOMMEND",
                    "inputAnchor":{"text":"推荐六个项目","start":0},
                    "subjectCandidates":[],"requestedOutputs":["RECOMMENDATION"],
                    "knowledgeRequirement":"PUBLIC_PORTFOLIO_EVIDENCE",
                    "parameters":{"kind":"PORTFOLIO_RECOMMEND",
                      "requestedSize":6,"constraints":[]}
                  }
                }
                """))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.NUMBER_CONSTRAINT_INVALID);
    }

    @Test
    void providerDraftArrayDiagnosticNamesFieldAndViolatedBound() {
        StructuredOutputValidationException failure = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> registry.validate(
                        new StructuredContractRef(
                                ModelOperation.GENERAL_KNOWLEDGE,
                                "general.provider-draft.v2"),
                        """
                        {"kind":"EXPLANATION","depth":"DETAILED",
                         "definitionSentences":["定义一","定义二","定义三"],
                         "mechanismSentences":["机制一","机制二","机制三","机制四"],
                         "caveats":[]}
                        """),
                        StructuredOutputValidationException.class);

        assertThat(failure.getReason()).isEqualTo(
                StructuredOutputValidationException.Reason.ARRAY_CONSTRAINT_INVALID);
        assertThat(failure.getDiagnosticReason()).isEqualTo(
                "ARRAY_CONSTRAINT_INVALID_DEFINITION_SENTENCES_MIN_ITEMS");
    }

    @Test
    void rejectsNonObjectAndOversizedPayloadWithoutEchoingContent() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2");

        assertThatThrownBy(() -> registry.validate(ref, "[]"))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.INVALID_JSON);
        String sentinel = "private-sentinel-" + "x".repeat(20000);
        assertThatThrownBy(() -> registry.validate(ref, sentinel))
                .isInstanceOf(StructuredOutputValidationException.class)
                .hasMessageNotContaining("private-sentinel")
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.OUTPUT_TOO_LARGE);
    }

    @Test
    void rejectsExcessiveJsonDepthBeforeTheOpenV4SchemaCanAdmitIt() throws Exception {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.provider-draft.v4");
        ObjectNode root = MAPPER.createObjectNode()
                .put("definition", "定义")
                .put("mechanism", "机制");
        ObjectNode cursor = root.putObject("caveats");
        for (int index = 0; index < 16; index++) {
            cursor = cursor.putObject("nested");
        }

        assertResourceLimit(() -> registry.validate(ref, root.toString()),
                "OUTPUT_TOO_LARGE_RESOURCE_NESTING_DEPTH");
        assertResourceLimit(() -> registry.validateTree(ref, root),
                "OUTPUT_TOO_LARGE_RESOURCE_NESTING_DEPTH");
    }

    @Test
    void acceptsDepthSixteenAndRejectsDepthSeventeenAtBothRegistryEntrypoints() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.provider-draft.v4");
        ObjectNode depthSixteen = nestedV4Draft(14);
        ObjectNode depthSeventeen = nestedV4Draft(15);

        assertThat(registry.validate(ref, depthSixteen.toString()).jsonTree())
                .isEqualTo(depthSixteen);
        assertThat(registry.validateTree(ref, depthSixteen).jsonTree())
                .isEqualTo(depthSixteen);
        assertResourceLimit(() -> registry.validate(
                        ref, depthSeventeen.toString()),
                "OUTPUT_TOO_LARGE_RESOURCE_NESTING_DEPTH");
        assertResourceLimit(() -> registry.validateTree(ref, depthSeventeen),
                "OUTPUT_TOO_LARGE_RESOURCE_NESTING_DEPTH");
    }

    @Test
    void rejectsExcessiveTotalArrayElementsBeforeInvalidV4CaveatsCanDegrade() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.provider-draft.v4");
        ObjectNode root = MAPPER.createObjectNode()
                .put("definition", "定义")
                .put("mechanism", "机制");
        ArrayNode caveats = root.putArray("caveats");
        for (int index = 0; index < 65; index++) {
            caveats.add("invalid optional value");
        }

        assertResourceLimit(() -> registry.validate(ref, root.toString()),
                "OUTPUT_TOO_LARGE_RESOURCE_ARRAY_ELEMENTS");
        assertResourceLimit(() -> registry.validateTree(ref, root),
                "OUTPUT_TOO_LARGE_RESOURCE_ARRAY_ELEMENTS");
    }

    @Test
    void acceptsSixtyFourAndRejectsSixtyFiveTotalArrayElementsAtBothEntrypoints() {
        StructuredContractRef ref = new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.provider-draft.v4");
        ObjectNode sixtyFour = arrayV4Draft(64);
        ObjectNode sixtyFive = arrayV4Draft(65);

        assertThat(registry.validate(ref, sixtyFour.toString()).jsonTree())
                .isEqualTo(sixtyFour);
        assertThat(registry.validateTree(ref, sixtyFour).jsonTree())
                .isEqualTo(sixtyFour);
        assertResourceLimit(() -> registry.validate(ref, sixtyFive.toString()),
                "OUTPUT_TOO_LARGE_RESOURCE_ARRAY_ELEMENTS");
        assertResourceLimit(() -> registry.validateTree(ref, sixtyFive),
                "OUTPUT_TOO_LARGE_RESOURCE_ARRAY_ELEMENTS");
    }

    @Test
    void typeFailureDiagnosticNamesOnlyAWhitelistedSchemaField() {
        StructuredOutputValidationException failure = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> registry.validate(
                        new StructuredContractRef(
                                ModelOperation.GENERAL_KNOWLEDGE,
                                "general.draft.v2"),
                        "{\"topic\":42,\"statements\":[{\"role\":\"DEFINITION\","
                                + "\"text\":\"定义。\",\"aspects\":[\"DEFINITION\"]}],"
                                + "\"caveats\":[]}"),
                        StructuredOutputValidationException.class);

        assertThat(failure.getReason())
                .isEqualTo(StructuredOutputValidationException.Reason.FIELD_TYPE_INVALID);
        assertThat(failure.getDiagnosticReason())
                .isEqualTo("FIELD_TYPE_INVALID_TOPIC");
    }

    @Test
    void rejectsUnknownOperationVersionWithoutEchoingIt() {
        assertThatThrownBy(() -> registry.resolve(new StructuredContractRef(
                ModelOperation.TURN_INTERPRETATION, "unknown.secret.value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("structured output contract is not approved")
                .hasMessageNotContaining("unknown.secret.value");
    }

    private void assertResourceLimit(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation,
            String diagnosticReason) {
        assertThatThrownBy(invocation)
                .isInstanceOf(StructuredOutputValidationException.class)
                .satisfies(failure -> {
                    StructuredOutputValidationException typed =
                            (StructuredOutputValidationException) failure;
                    assertThat(typed.getReason()).isEqualTo(
                            StructuredOutputValidationException.Reason.OUTPUT_TOO_LARGE);
                    assertThat(typed.getDiagnosticReason()).isEqualTo(diagnosticReason);
                });
    }

    private ObjectNode nestedV4Draft(int nestedObjectCount) {
        ObjectNode root = MAPPER.createObjectNode()
                .put("definition", "定义")
                .put("mechanism", "机制");
        ObjectNode cursor = root.putObject("caveats");
        for (int index = 0; index < nestedObjectCount; index++) {
            cursor = cursor.putObject("nested");
        }
        return root;
    }

    private ObjectNode arrayV4Draft(int size) {
        ObjectNode root = MAPPER.createObjectNode()
                .put("definition", "定义")
                .put("mechanism", "机制");
        ArrayNode caveats = root.putArray("caveats");
        for (int index = 0; index < size; index++) {
            caveats.add("optional");
        }
        return root;
    }
}
