package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralDraftValidatorTest {
    private final GeneralDraftCodec codec = new GeneralDraftCodec(new ObjectMapper());
    private final GeneralDraftValidator validator = new GeneralDraftValidator();

    @Test void validExplanationBecomesMinimalSemanticResult() {
        GeneralSemanticResult result = validator.validate(
                GeneralTestFixtures.explanation(), codec.decode(GeneralTestFixtures.VALID_EXPLANATION));
        assertThat(result.getStatements()).extracting(GeneralSemanticResult.Statement::getRole)
                .containsExactly(GeneralSemanticResult.Role.DEFINITION, GeneralSemanticResult.Role.MECHANISM);
        assertThat(result.getContentVersion()).isEqualTo("public-1");
    }

    @Test void missingMechanismFailsInsteadOfBecomingPartial() {
        String incomplete = GeneralTestFixtures.VALID_EXPLANATION.replace(
                ",\n  {\"role\":\"MECHANISM\",\"text\":\"它通过有界调度与状态隔离控制竞争。具体机制需要服从运行环境的边界。\",\"aspects\":[\"MECHANISM\",\"APPLICABILITY_BOUNDARY\"]}", "");
        assertThatThrownBy(() -> validator.validate(
                GeneralTestFixtures.explanation(), codec.decode(incomplete)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateDefinitionFailsClosed() {
        assertRejected("""
                {"role":"DEFINITION","text":"定义一。","aspects":["DEFINITION"]},
                {"role":"DEFINITION","text":"定义二。","aspects":["DEFINITION"]},
                {"role":"MECHANISM","text":"机制。","aspects":["MECHANISM"]}
                """);
    }

    @Test void duplicateMechanismFailsClosed() {
        assertRejected("""
                {"role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]},
                {"role":"MECHANISM","text":"机制一。","aspects":["MECHANISM"]},
                {"role":"MECHANISM","text":"机制二。","aspects":["MECHANISM"]}
                """);
    }

    @Test void comparisonRoleInsideExplanationFailsClosed() {
        assertRejected("""
                {"role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]},
                {"role":"COMPARISON","text":"比较。","subject":"并发控制","dimension":"机制","aspects":[]},
                {"role":"MECHANISM","text":"机制。","aspects":["MECHANISM"]}
                """);
    }

    @Test void mechanismBeforeDefinitionFailsClosed() {
        assertRejected("""
                {"role":"MECHANISM","text":"机制。","aspects":["MECHANISM"]},
                {"role":"DEFINITION","text":"定义。","aspects":["DEFINITION"]}
        """);
    }

    @Test void runtimeEnforcesDepthSentenceCountsAndChineseSentences() {
        String tooShort = """
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"定义。","aspects":["DEFINITION","TYPICAL_USAGE"]},
                  {"role":"MECHANISM","text":"机制。","aspects":["MECHANISM","APPLICABILITY_BOUNDARY"]}
                ],"caveats":[]}
                """;
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.STANDARD), codec.decode(tooShort)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sentence count");

        String englishSentence = GeneralTestFixtures.VALID_EXPLANATION.replace(
                "它常用于共享资源访问。", "This is a complete English sentence。");
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.STANDARD), codec.decode(englishSentence)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
    }

    @Test void standardAcceptsTheApprovedTotalBucketWithoutPerRoleSymmetry() {
        String unevenButComplete = """
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"并发控制协调共享资源访问。","aspects":["DEFINITION","TYPICAL_USAGE"]},
                  {"role":"MECHANISM","text":"它通过排序约束竞争。它常用于并发写入。适用范围取决于一致性要求。","aspects":["MECHANISM","APPLICABILITY_BOUNDARY"]}
                ],"caveats":[]}
                """;

        assertThat(validator.validate(
                explanation(UserGoalProposal.Depth.STANDARD),
                codec.decode(unevenButComplete)).getStatements()).hasSize(2);
    }

    @Test void detailedAcceptsEightToTwelveChineseSentencesInTotal() {
        String unevenButComplete = """
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"并发控制协调共享资源访问。它用于多任务共同读写数据。常见误区是并行越多越快。","aspects":["DEFINITION","TYPICAL_USAGE","COMMON_MISCONCEPTION"]},
                  {"role":"MECHANISM","text":"它通过排序约束竞争。它也可以通过隔离保护状态。协调过程会产生性能取舍。适用范围取决于一致性要求。边界条件包括故障恢复能力。","aspects":["MECHANISM","TRADE_OFF","APPLICABILITY_BOUNDARY","BOUNDARY_CONDITION"]}
                ],"caveats":[]}
                """;

        assertThat(validator.validate(
                explanation(UserGoalProposal.Depth.DETAILED),
                codec.decode(unevenButComplete)).getStatements()).hasSize(2);
    }

    @Test void detailedRequiresEveryApprovedSemanticAspect() {
        String missingTradeOff = """
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"这是定义。它用于共享资源。它说明常见用法。它澄清常见误区。","aspects":["DEFINITION","TYPICAL_USAGE","COMMON_MISCONCEPTION"]},
                  {"role":"MECHANISM","text":"它协调执行。它限制竞争。它说明适用边界。它说明边界条件。","aspects":["MECHANISM","APPLICABILITY_BOUNDARY","BOUNDARY_CONDITION"]}
                ],"caveats":[]}
                """;
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.DETAILED), codec.decode(missingTradeOff)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semantic coverage");
    }

    @Test void detailedAcceptsExactSentenceAndAspectCoverage() {
        String detailed = """
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"并发控制协调共享资源访问。它用于多任务共同读写数据。典型用法包括锁和事务。常见误区是并行越多越快。","aspects":["DEFINITION","TYPICAL_USAGE","COMMON_MISCONCEPTION"]},
                  {"role":"MECHANISM","text":"它通过排序或隔离约束竞争。额外协调会带来性能取舍。适用范围取决于一致性要求。边界条件包括故障恢复能力。","aspects":["MECHANISM","TRADE_OFF","APPLICABILITY_BOUNDARY","BOUNDARY_CONDITION"]}
                ],"caveats":[]}
                """;
        GeneralSemanticResult result = validator.validate(
                explanation(UserGoalProposal.Depth.DETAILED), codec.decode(detailed));
        assertThat(result.getStatements()).hasSize(2);
    }

    @Test void comparisonPairsMustEqualTheRequestedCartesianProduct() {
        GeneralKnowledgeRequest request = GeneralKnowledgeRequest.comparison(
                List.of("A", "B"), Set.of("MECHANISM"),
                GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                TurnDeadline.after(Duration.ofSeconds(5), Clock.systemUTC()));
        String duplicate = """
                {"topic":"A vs B","statements":[
                  {"role":"COMPARISON","text":"甲采用一种机制。","subject":"A","dimension":"MECHANISM","aspects":[]},
                  {"role":"COMPARISON","text":"甲仍采用该机制。","subject":"A","dimension":"MECHANISM","aspects":[]},
                  {"role":"COMPARISON","text":"乙采用另一机制。","subject":"B","dimension":"MECHANISM","aspects":[]}
                ],"caveats":[]}
                """;
        assertThatThrownBy(() -> validator.validate(request, codec.decode(duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate pair");

        String extra = """
                {"topic":"A vs B","statements":[
                  {"role":"COMPARISON","text":"甲采用一种机制。","subject":"A","dimension":"MECHANISM","aspects":[]},
                  {"role":"COMPARISON","text":"乙采用另一机制。","subject":"B","dimension":"MECHANISM","aspects":[]},
                  {"role":"COMPARISON","text":"丙不在请求范围。","subject":"C","dimension":"MECHANISM","aspects":[]}
                ],"caveats":[]}
                """;
        assertThatThrownBy(() -> validator.validate(request, codec.decode(extra)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    @Test void caveatsRequireUniqueClosedKindsAndChineseText() {
        String duplicateKind = GeneralTestFixtures.VALID_EXPLANATION.replace(
                "{\"kind\":\"RISK\",\"text\":\"错误的锁策略可能降低吞吐量。\"}",
                "{\"kind\":\"RISK\",\"text\":\"错误的锁策略可能降低吞吐量。\"},"
                        + "{\"kind\":\"RISK\",\"text\":\"错误策略也可能导致饥饿。\"}");
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.STANDARD), codec.decode(duplicateKind)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");

        String english = GeneralTestFixtures.VALID_EXPLANATION.replace(
                "错误的锁策略可能降低吞吐量。", "This caveat is entirely English。");
        assertThatThrownBy(() -> validator.validate(
                explanation(UserGoalProposal.Depth.STANDARD), codec.decode(english)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
    }

    private void assertRejected(String statements) {
        String json = """
                {"topic":"并发控制","statements":[%s],"caveats":[]}
                """.formatted(statements);
        assertThatThrownBy(() -> validator.validate(
                GeneralTestFixtures.explanation(), codec.decode(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explanation roles");
    }

    private GeneralKnowledgeRequest explanation(UserGoalProposal.Depth depth) {
        return GeneralKnowledgeRequest.explanation(
                "并发控制", depth, GeneralKnowledgeRequest.Audience.GUEST,
                "public-1", TurnDeadline.after(
                        Duration.ofSeconds(5), Clock.systemUTC()));
    }
}
