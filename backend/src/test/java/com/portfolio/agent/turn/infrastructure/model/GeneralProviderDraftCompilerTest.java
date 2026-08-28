package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralProviderDraftCompilerTest {
    private static final StructuredContractRef PROVIDER_CONTRACT =
            new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                    "general.provider-draft.v4");
    private static final StructuredContractRef CANONICAL_CONTRACT =
            new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                    "general.draft.v3");
    private final ObjectMapper mapper = new ObjectMapper();
    private final StructuredOutputContractRegistry registry =
            StructuredOutputContractRegistry.standard();
    private final GeneralDraftCodec codec = new GeneralDraftCodec(mapper);
    private final GeneralDraftValidator validator = new GeneralDraftValidator();

    @ParameterizedTest
    @MethodSource("validDepthCases")
    void compilesEveryDepthIntoExactlyTwoTrustedStatements(
            UserGoalProposal.Depth depth, String definition, String mechanism,
            int expectedSentences) {
        GeneralKnowledgeRequest request = explanation(depth);

        JsonNode canonical = compile(request, """
                {"definition":%s,"mechanism":%s,"caveats":[]}
                """.formatted(definition, mechanism));

        assertThat(canonical.path("topic").textValue()).isEqualTo("并发控制");
        assertThat(canonical.path("statements")).hasSize(2);
        assertThat(canonical.path("statements").get(0).path("role").textValue())
                .isEqualTo("DEFINITION");
        assertThat(canonical.path("statements").get(0).path("aspects"))
                .extracting(JsonNode::textValue).containsExactly("DEFINITION");
        assertThat(canonical.path("statements").get(1).path("role").textValue())
                .isEqualTo("MECHANISM");
        assertThat(canonical.path("statements").get(1).path("aspects"))
                .extracting(JsonNode::textValue).containsExactly("MECHANISM");
        canonical.path("statements").forEach(statement -> {
            assertThat(statement.path("subject").isNull()).isTrue();
            assertThat(statement.path("dimension").isNull()).isTrue();
        });
        assertThat(countNaturalSentences(canonical)).isEqualTo(expectedSentences);
        assertCanonicalAndSemantic(request, canonical);
    }

    @Test
    void normalizesTextInTheFrozenOrderAndJoinsOnlyWithinEachRole() {
        GeneralKnowledgeRequest request = explanation(UserGoalProposal.Depth.STANDARD);

        JsonNode canonical = compile(request, """
                {"definition":["  Café\\t是一种名称  ","它\\n用于说明归一化！”"],
                 "mechanism":["第一步解析输入；保留分号","第二步输出结果"],
                 "topic":"模型不能覆盖可信主题","unknown":{"value":"不进入结果"}}
                """);

        assertThat(canonical.path("statements").get(0).path("text").textValue())
                .isEqualTo("Café 是一种名称。 它 用于说明归一化。”");
        assertThat(canonical.path("statements").get(1).path("text").textValue())
                .isEqualTo("第一步解析输入；保留分号。 第二步输出结果。");
        assertThat(canonical.path("topic").textValue()).isEqualTo("并发控制");
        assertThat(canonical.toString()).doesNotContain("模型不能覆盖", "unknown");
        assertCanonicalAndSemantic(request, canonical);
    }

    @ParameterizedTest
    @MethodSource("normalizationCases")
    void textNormalizationIsIdempotent(String raw, String expected) {
        String once = GeneralProviderDraftCompiler.normalizeText(raw);

        assertThat(once).isEqualTo(expected);
        assertThat(GeneralProviderDraftCompiler.normalizeText(once)).isEqualTo(once);
    }

    @Test
    void missingNullOrMalformedCaveatsBecomeAnEmptyCanonicalArray() {
        for (String suffix : new String[] {
                "", ",\"caveats\":null", ",\"caveats\":42",
                ",\"caveats\":[{\"kind\":\"RISK\",\"sentences\":\"合法说明\"},42]",
                ",\"caveats\":[{\"kind\":\"UNKNOWN\",\"sentences\":\"说明\"}]"}) {
            JsonNode canonical = compile(explanation(UserGoalProposal.Depth.CONCISE),
                    "{\"definition\":\"定义\",\"mechanism\":\"机制\"" + suffix + "}");

            assertThat(canonical.path("caveats")).isEmpty();
            assertCanonicalAndSemantic(
                    explanation(UserGoalProposal.Depth.CONCISE), canonical);
        }
    }

    @Test
    void validCaveatsAreNormalizedWithoutPartialSalvage() {
        JsonNode canonical = compile(explanation(UserGoalProposal.Depth.CONCISE), """
                {"definition":"定义","mechanism":"机制",
                 "caveats":[{"kind":"RISK","sentences":[" 风险一 ","风险二？”"]}]}
                """);

        assertThat(canonical.path("caveats")).hasSize(1);
        assertThat(canonical.path("caveats").get(0).path("text").textValue())
                .isEqualTo("风险一。 风险二。”");
        assertCanonicalAndSemantic(explanation(UserGoalProposal.Depth.CONCISE), canonical);
    }

    @Test
    void compilerRequiresChineseToDominateLatinLettersButAllowsTechnicalTerms() {
        assertThatThrownBy(() -> compile(
                explanation(UserGoalProposal.Depth.CONCISE),
                "{\"definition\":\"中This is mostly English\",\"mechanism\":\"机制\"}"))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("diagnosticReason")
                .isEqualTo("DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_LANGUAGE");

        JsonNode canonical = compile(explanation(UserGoalProposal.Depth.CONCISE),
                "{\"definition\":\"JWT令牌用于鉴权\","
                        + "\"mechanism\":\"TLS协议保护传输\"}");
        assertCanonicalAndSemantic(explanation(UserGoalProposal.Depth.CONCISE), canonical);
    }

    @ParameterizedTest
    @MethodSource("invalidCoreCases")
    void rejectsInvalidCoreInsteadOfRepairingOrClipping(
            UserGoalProposal.Depth depth, String providerDraft) throws Exception {
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                explanation(depth));

        assertThatThrownBy(() -> compiler.compile(mapper.readTree(providerDraft)))
                .isInstanceOf(StructuredOutputValidationException.class);
    }

    @Test
    void comparisonRequestsCannotEnterTheExplanationCompiler() throws Exception {
        GeneralKnowledgeRequest comparison = GeneralKnowledgeRequest.comparison(
                java.util.List.of("Redis", "Memcached"), java.util.Set.of("MECHANISM"),
                GeneralKnowledgeRequest.Audience.GUEST, "public-1", deadline());

        assertThatThrownBy(() -> new GeneralProviderDraftCompiler(comparison).compile(
                mapper.readTree("{\"definition\":\"定义\",\"mechanism\":\"机制\"}")))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
    }

    private JsonNode compile(GeneralKnowledgeRequest request, String raw) {
        StructurallyValidatedOutput admitted = registry.validate(PROVIDER_CONTRACT, raw);
        return new GeneralProviderDraftCompiler(request).compile(admitted.jsonTree());
    }

    private void assertCanonicalAndSemantic(
            GeneralKnowledgeRequest request, JsonNode canonical) {
        StructurallyValidatedOutput validated = registry.validateTree(
                CANONICAL_CONTRACT, canonical);
        assertThat(validator.validate(request, codec.decode(validated)).getStatements())
                .hasSize(2);
    }

    private int countNaturalSentences(JsonNode canonical) {
        int count = 0;
        for (JsonNode statement : canonical.path("statements")) {
            String text = statement.path("text").textValue();
            count += (int) text.codePoints()
                    .filter(value -> "。！？!?".indexOf(value) >= 0)
                    .count();
        }
        return count;
    }

    private static Stream<Arguments> validDepthCases() {
        return Stream.of(
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "\"并发控制协调共享资源访问\"",
                        "\"它通过排序或隔离约束竞争\"", 2),
                Arguments.of(UserGoalProposal.Depth.STANDARD,
                        "[\"并发控制协调共享资源访问\",\"它减少竞争条件\"]",
                        "[\"它通过排序约束竞争\",\"隔离机制保护状态\"]", 4),
                Arguments.of(UserGoalProposal.Depth.DETAILED,
                        "[\"定义一\",\"定义二\",\"定义三\",\"定义四\"]",
                        "[\"机制一\",\"机制二\",\"机制三\",\"机制四\"]", 8));
    }

    private static Stream<Arguments> normalizationCases() {
        return Stream.of(
                Arguments.of("  定义  ", "定义。"),
                Arguments.of("机制！", "机制。"),
                Arguments.of("机制？”", "机制。”"),
                Arguments.of("A\u030A\t术语", "Å 术语。"),
                Arguments.of("\u00a0\u3000定义\u2003机制\u00a0", "定义 机制。"),
                Arguments.of("保留；分号：括号（值）", "保留；分号：括号（值。）"));
    }

    private static Stream<Arguments> invalidCoreCases() {
        return Stream.of(
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "{\"definition\":\"   \",\"mechanism\":\"机制\"}"),
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "{\"definition\":42,\"mechanism\":\"机制\"}"),
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "{\"definition\":[\"定义\",42],\"mechanism\":\"机制\"}"),
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "{\"definition\":\"定义一。定义二\",\"mechanism\":\"机制\"}"),
                Arguments.of(UserGoalProposal.Depth.STANDARD,
                        "{\"definition\":\"定义一。定义二。定义三。定义四\",\"mechanism\":\"机制\"}"),
                Arguments.of(UserGoalProposal.Depth.DETAILED,
                        "{\"definition\":\"定义一。定义二。定义三。\",\"mechanism\":\"机制一。机制二。机制三。机制四。\"}"));
    }

    private GeneralKnowledgeRequest explanation(UserGoalProposal.Depth depth) {
        return GeneralKnowledgeRequest.explanation(
                "并发控制", depth, GeneralKnowledgeRequest.Audience.GUEST,
                "public-1", deadline());
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(Duration.ofSeconds(5), Clock.systemUTC());
    }
}
