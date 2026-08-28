package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralDraftValidatorTest {
    private final GeneralDraftCodec codec = new GeneralDraftCodec(new ObjectMapper());
    private final GeneralDraftValidator validator = new GeneralDraftValidator();
    private final StructuredOutputContractRegistry contracts =
            StructuredOutputContractRegistry.standard();

    @Test
    void validExplanationBecomesMinimalSemanticResult() {
        GeneralSemanticResult result = validator.validate(
                GeneralTestFixtures.explanation(),
                codec.decode(GeneralTestFixtures.VALID_EXPLANATION));

        assertThat(result.getStatements())
                .extracting(GeneralSemanticResult.Statement::getRole)
                .containsExactly(GeneralSemanticResult.Role.DEFINITION,
                        GeneralSemanticResult.Role.MECHANISM);
        assertThat(result.getContentVersion()).isEqualTo("public-1");
    }

    @ParameterizedTest
    @MethodSource("validDepthBoundaries")
    void validatesEachRoleAndTotalNaturalSentenceBoundary(
            UserGoalProposal.Depth depth, String definition, String mechanism) {
        GeneralSemanticResult result = validator.validate(
                explanation(depth), codec.decode(draft(definition, mechanism)));

        assertThat(result.getStatements()).hasSize(2);
    }

    @ParameterizedTest
    @MethodSource("invalidDepthBoundaries")
    void rejectsEitherRoleOutsideItsDepthBoundary(
            UserGoalProposal.Depth depth, String definition, String mechanism) {
        assertThatThrownBy(() -> validator.validate(
                explanation(depth), codec.decode(draft(definition, mechanism))))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("sentence count");
    }

    @Test
    void roleAspectsMustBeExactlyTheTrustedRoleAspect() {
        String extraAspect = draft("定义。", "机制。").replace(
                "[\"DEFINITION\"]",
                "[\"DEFINITION\",\"TYPICAL_USAGE\"]");
        String swappedAspect = draft("定义。", "机制。").replace(
                "[\"MECHANISM\"]", "[\"DEFINITION\"]");

        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(extraAspect)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("role aspects");
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(swappedAspect)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("role aspects");
    }

    @Test
    void explanationRejectsProviderIdentityFieldsAndWrongRoleOrder() {
        String subjectEcho = draft("定义。", "机制。").replace(
                "\"subject\":null", "\"subject\":\"模型回显\"");
        String wrongOrder = draft("定义。", "机制。")
                .replace("\"role\":\"DEFINITION\"", "\"role\":\"TEMP\"")
                .replace("\"role\":\"MECHANISM\"", "\"role\":\"DEFINITION\"")
                .replace("\"role\":\"TEMP\"", "\"role\":\"MECHANISM\"");

        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(subjectEcho)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("roles");
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(wrongOrder)))
                .isInstanceOf(GeneralDraftValidationException.class);
    }

    @Test
    void validatorChecksLanguageAndTopicWithoutPretendingToJudgeCoverage() {
        String english = draft("This sentence is English。", "机制。");
        String anotherTopic = draft("定义。", "机制。")
                .replace("\"topic\":\"并发控制\"", "\"topic\":\"其他主题\"");

        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(english)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("language");
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(anotherTopic)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void chineseMustBeTheDominantLetterScriptWhileTechnicalTermsRemainLegal() {
        String englishBody = draft("中This sentence is mostly English。", "机制。");
        String technicalTerm = draft("JWT令牌用于鉴权。", "TLS协议保护传输。");

        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), codec.decode(englishBody)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("language");
        assertThat(validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE),
                codec.decode(technicalTerm)).getStatements()).hasSize(2);
    }

    @Test
    void canonicalV3AllowsMoreThanOneUsefulCaveatOfTheSameKind() {
        String repeatedKindAndText = draft("定义。", "机制。").replace(
                "\"caveats\":[]",
                "\"caveats\":["
                        + "{\"kind\":\"RISK\",\"text\":\"锁竞争可能降低吞吐量。\"},"
                        + "{\"kind\":\"RISK\",\"text\":\"锁顺序不当可能形成死锁。\"},"
                        + "{\"kind\":\"EXCEPTION\",\"text\":\"锁竞争可能降低吞吐量。\"}]");

        assertThat(validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE),
                codec.decode(repeatedKindAndText)).getCaveats()).hasSize(3);
    }

    @Test
    void canonicalV2RetainsItsPublishedCaveatUniquenessRule() {
        String repeatedKind = draft("定义。", "机制。").replace(
                "\"caveats\":[]",
                "\"caveats\":["
                        + "{\"kind\":\"RISK\",\"text\":\"锁竞争可能降低吞吐量。\"},"
                        + "{\"kind\":\"RISK\",\"text\":\"锁顺序不当可能形成死锁。\"}]");
        GeneralDraftCodec.Draft v2 = codec.decode(contracts.validate(
                new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                        "general.draft.v2"), repeatedKind));

        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE), v2))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("duplicates");
    }

    @Test
    void emptyCaveatsRemainACompleteExplanation() {
        GeneralSemanticResult result = validator.validate(
                explanation(UserGoalProposal.Depth.CONCISE),
                codec.decode(draft("定义。", "机制。")));

        assertThat(result.getCaveats()).isEmpty();
    }

    @Test
    void historicalComparisonValidationRemainsFailClosed() {
        GeneralKnowledgeRequest request = GeneralKnowledgeRequest.comparison(
                List.of("A", "B"), Set.of("MECHANISM"),
                GeneralKnowledgeRequest.Audience.GUEST, "public-1", deadline());
        String duplicate = """
                {"topic":"A vs B","statements":[
                  {"role":"COMPARISON","text":"甲采用一种机制。","subject":"A",
                   "dimension":"MECHANISM","aspects":[]},
                  {"role":"COMPARISON","text":"甲仍采用该机制。","subject":"A",
                   "dimension":"MECHANISM","aspects":[]},
                  {"role":"COMPARISON","text":"乙采用另一机制。","subject":"B",
                   "dimension":"MECHANISM","aspects":[]}],"caveats":[]}
                """;

        assertThatThrownBy(() -> validator.validate(request, decodeV2(duplicate)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining("duplicate pair");
    }

    @Test
    void historicalComparisonKeepsPeriodRoleAspectAndExactPairRules() {
        GeneralKnowledgeRequest request = GeneralKnowledgeRequest.comparison(
                List.of("A", "B"), Set.of("MECHANISM"),
                GeneralKnowledgeRequest.Audience.GUEST, "public-1", deadline());
        String valid = comparisonDraft(
                comparisonStatement("A", "甲采用机制；适合一种场景。", "COMPARISON"),
                comparisonStatement("B", "乙采用另一机制。", "COMPARISON"));

        assertThat(validator.validate(request, decodeV2(valid)).getStatements())
                .hasSize(2);
        assertComparisonRejected(request, valid.replace(
                "\"role\":\"COMPARISON\"", "\"role\":\"DEFINITION\""),
                "role");
        assertComparisonRejected(request, valid.replace(
                "\"aspects\":[]", "\"aspects\":[\"MECHANISM\"]"),
                "aspects");
        assertComparisonRejected(request, comparisonDraft(
                comparisonStatement("A", "甲采用一种机制。", "COMPARISON")),
                "do not match");
        assertComparisonRejected(request, comparisonDraft(
                comparisonStatement("A", "甲采用一种机制。", "COMPARISON"),
                comparisonStatement("B", "乙采用另一机制。", "COMPARISON"),
                comparisonStatement("C", "丙属于额外对象。", "COMPARISON")),
                "do not match");
    }

    @ParameterizedTest
    @ValueSource(strings = {"！", "？", "!", "?", "."})
    void historicalComparisonRejectsEveryNonChinesePeriodTerminator(
            String terminator) {
        GeneralKnowledgeRequest request = GeneralKnowledgeRequest.comparison(
                List.of("A", "B"), Set.of("MECHANISM"),
                GeneralKnowledgeRequest.Audience.GUEST, "public-1", deadline());
        String invalid = comparisonDraft(
                comparisonStatement("A", "甲采用一种机制" + terminator,
                        "COMPARISON"),
                comparisonStatement("B", "乙采用另一机制。", "COMPARISON"));

        assertComparisonRejected(request, invalid, "boundaries");
    }

    private static Stream<Arguments> validDepthBoundaries() {
        return Stream.of(
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "定义。", "机制。"),
                Arguments.of(UserGoalProposal.Depth.STANDARD,
                        "定义一。", "机制一。机制二。机制三。"),
                Arguments.of(UserGoalProposal.Depth.STANDARD,
                        "定义一。定义二。定义三。", "机制一。"),
                Arguments.of(UserGoalProposal.Depth.DETAILED,
                        "定义一。定义二。定义三。定义四。",
                        "机制一。机制二。机制三。机制四。"),
                Arguments.of(UserGoalProposal.Depth.DETAILED,
                        "定义一。定义二。定义三。定义四。定义五。定义六。",
                        "机制一。机制二。机制三。机制四。机制五。机制六。"));
    }

    private static Stream<Arguments> invalidDepthBoundaries() {
        return Stream.of(
                Arguments.of(UserGoalProposal.Depth.CONCISE,
                        "定义一。定义二。", "机制。"),
                Arguments.of(UserGoalProposal.Depth.STANDARD,
                        "定义一。定义二。定义三。定义四。", "机制。"),
                Arguments.of(UserGoalProposal.Depth.DETAILED,
                        "定义一。定义二。定义三。",
                        "机制一。机制二。机制三。机制四。"),
                Arguments.of(UserGoalProposal.Depth.DETAILED,
                        "定义一。定义二。定义三。定义四。定义五。定义六。定义七。",
                        "机制一。机制二。机制三。机制四。"));
    }

    private String draft(String definition, String mechanism) {
        return """
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"%s","subject":null,
                   "dimension":null,"aspects":["DEFINITION"]},
                  {"role":"MECHANISM","text":"%s","subject":null,
                   "dimension":null,"aspects":["MECHANISM"]}],"caveats":[]}
                """.formatted(definition, mechanism);
    }

    private GeneralDraftCodec.Draft decodeV2(String raw) {
        return codec.decode(contracts.validate(new StructuredContractRef(
                ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"), raw));
    }

    private void assertComparisonRejected(
            GeneralKnowledgeRequest request, String raw, String message) {
        assertThatThrownBy(() -> validator.validate(request, decodeV2(raw)))
                .isInstanceOf(GeneralDraftValidationException.class)
                .hasMessageContaining(message);
    }

    private String comparisonStatement(String subject, String text, String role) {
        return "{\"role\":\"%s\",\"text\":\"%s\",\"subject\":\"%s\","
                .formatted(role, text, subject)
                + "\"dimension\":\"MECHANISM\",\"aspects\":[]}";
    }

    private String comparisonDraft(String... statements) {
        return "{\"topic\":\"A vs B\",\"statements\":["
                + String.join(",", statements) + "],\"caveats\":[]}";
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
