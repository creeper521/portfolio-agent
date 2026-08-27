package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralProviderDraftCompilerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final StructuredOutputContractRegistry registry =
            StructuredOutputContractRegistry.standard();
    private final GeneralDraftCodec codec = new GeneralDraftCodec(mapper);
    private final GeneralDraftValidator validator = new GeneralDraftValidator();

    @Test
    void explanationInjectsTrustedTopicAndRolesAndPassesCanonicalBoundaries()
            throws Exception {
        GeneralKnowledgeRequest request = explanation();
        JsonNode canonical = new GeneralProviderDraftCompiler(request).compile(
                providerDraft("""
                        {
                          "kind":"EXPLANATION",
                          "depth":"STANDARD",
                          "definitionSentences":[
                            "并发控制协调同时发生的工作。",
                            "它常用于共享资源访问"
                          ],
                          "mechanismSentences":[
                            "它通过有界调度与状态隔离控制竞争",
                            "具体机制需要服从运行环境的边界"
                          ],
                          "caveats":[{
                            "kind":"RISK","sentences":["错误的锁策略可能降低吞吐量"]
                          }]
                        }
                        """));

        assertThat(canonical.path("topic").textValue()).isEqualTo("并发控制");
        assertThat(canonical.path("statements").get(0).path("role").textValue())
                .isEqualTo("DEFINITION");
        assertThat(canonical.path("statements").get(1).path("role").textValue())
                .isEqualTo("MECHANISM");
        assertCanonicalAndSemantic(request, canonical);
    }

    @Test
    void comparisonInjectsTrustedCartesianPairsAndEmptyAspects()
            throws Exception {
        GeneralKnowledgeRequest request = comparison(
                List.of("Redis", "Memcached"), Set.of("MECHANISM", "TRADE_OFF"));
        JsonNode canonical = new GeneralProviderDraftCompiler(request).compile(
                providerDraft("""
                        {
                          "kind":"COMPARISON",
                          "comparisonSentences":[
                            {"text":"Memcached 采用多线程处理简单键值访问",
                             "dimension":"MECHANISM","subjectIndex":2},
                            {"text":"Redis 以丰富数据结构换取更多内存开销",
                             "dimension":"TRADE_OFF","subjectIndex":1},
                            {"text":"Memcached 以较少能力换取更简单的内存模型",
                             "dimension":"TRADE_OFF","subjectIndex":2},
                            {"text":"Redis 通过单线程事件循环处理命令",
                             "dimension":"MECHANISM","subjectIndex":1}
                          ],
                          "caveats":[]
                        }
                        """));

        assertThat(canonical.path("topic").textValue())
                .isEqualTo("Redis vs Memcached");
        assertThat(canonical.path("statements")).hasSize(4);
        record Pair(String subject, String dimension) {}
        java.util.List<Pair> declared = new java.util.ArrayList<>();
        canonical.path("statements").forEach(statement -> {
            assertThat(statement.path("role").textValue()).isEqualTo("COMPARISON");
            assertThat(statement.path("aspects")).isEmpty();
            declared.add(new Pair(
                    statement.path("subject").textValue(),
                    statement.path("dimension").textValue()));
        });
        assertThat(declared).containsExactly(
                new Pair("Redis", "MECHANISM"),
                new Pair("Redis", "TRADE_OFF"),
                new Pair("Memcached", "MECHANISM"),
                new Pair("Memcached", "TRADE_OFF"));
        assertThat(canonical.path("statements").get(0).path("text").textValue())
                .isEqualTo("Redis 通过单线程事件循环处理命令。");
        assertThat(canonical.path("statements").get(1).path("text").textValue())
                .isEqualTo("Redis 以丰富数据结构换取更多内存开销。");
        assertCanonicalAndSemantic(request, canonical);
    }

    @Test
    void comparisonKeepsOneProviderItemPerTrustedPairAndAllowsSemicolonClauses()
            throws Exception {
        GeneralKnowledgeRequest request = comparison(
                List.of("Redis", "Memcached"), Set.of("MECHANISM"));
        JsonNode canonical = new GeneralProviderDraftCompiler(request).compile(
                mapper.readTree("""
                {"kind":"COMPARISON",
                 "comparisonSentences":[
                   {"text":"Memcached 使用多线程；操作模型更简单",
                    "dimension":"MECHANISM","subjectIndex":2},
                   {"text":"Redis 使用事件循环；命令按序执行",
                    "dimension":"MECHANISM","subjectIndex":1}
                 ],"caveats":[]}
                """));

        assertThat(canonical.path("statements")).hasSize(2);
        assertThat(canonical.path("statements").get(0).path("text").textValue())
                .isEqualTo("Redis 使用事件循环；命令按序执行。");
        assertCanonicalAndSemantic(request, canonical);
    }

    @Test
    void legacyScalarExplanationSequenceCompilesWithoutChangingContent()
            throws Exception {
        GeneralKnowledgeRequest explanation = explanation();
        JsonNode explanationCanonical = new GeneralProviderDraftCompiler(
                explanation).compile(mapper.readTree("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":"并发控制协调同时发生的工作。它常用于共享资源访问。",
                 "mechanismSentences":"它通过状态隔离控制竞争。具体机制服从运行环境边界。",
                 "caveats":[]}
                """));
        assertThat(explanationCanonical.path("statements").get(0)
                .path("text").textValue()).isEqualTo(
                "并发控制协调同时发生的工作。它常用于共享资源访问。");
        assertCanonicalAndSemantic(explanation, explanationCanonical);
    }

    @Test
    void arraySentenceChunksAreFlattenedWithoutGeneratingOrDroppingContent()
            throws Exception {
        GeneralKnowledgeRequest request = explanation();
        JsonNode canonical = new GeneralProviderDraftCompiler(request).compile(
                mapper.readTree("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["并发控制协调工作。它用于共享资源访问。"],
                 "mechanismSentences":["状态隔离控制竞争。具体机制服从环境边界。"],
                 "caveats":[]}
                """));

        assertThat(canonical.path("statements").get(0).path("text").textValue())
                .isEqualTo("并发控制协调工作。它用于共享资源访问。");
        assertCanonicalAndSemantic(request, canonical);
    }

    @Test
    void leadingTechnicalLabelIsBoundToItsChineseSentenceDeterministically()
            throws Exception {
        GeneralKnowledgeRequest request = explanation();
        JsonNode canonical = new GeneralProviderDraftCompiler(request).compile(
                mapper.readTree("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":[
                   "JSON Web Token（JWT）","它是一种令牌","它用于传递声明"
                 ],
                 "mechanismSentences":["签名保护声明完整性。服务端校验签名和期限。"],
                 "caveats":[]}
                """));

        assertThat(canonical.path("statements").get(0).path("text").textValue())
                .isEqualTo(
                        "JSON Web Token（JWT）：它是一种令牌。它用于传递声明。");
        assertCanonicalAndSemantic(request, canonical);
    }

    @Test
    void rejectsNonChineseSegmentsOutsideTheSingleLeadingLabelPosition()
            throws Exception {
        assertRejectedWithDiagnostic(new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["这是定义。JSON Web Token。"],
                 "mechanismSentences":["这是机制。这是边界。"],"caveats":[]}
                """, StructuredOutputValidationException.Reason
                .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_TRAILING");

        assertRejectedWithDiagnostic(
                new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["this is an english sentence","这是定义"],
                 "mechanismSentences":["这是机制。这是边界。"],"caveats":[]}
                """, StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_TECHNICAL_LABEL_CASE");

        assertRejectedWithDiagnostic(
                new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["1。这里是定义。这里是用途。"],
                 "mechanismSentences":["这是机制。这是边界。"],"caveats":[]}
                """, StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_NUMBER_MARKER");

        assertRejectedWithDiagnostic(
                new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["…","这里是定义","这里是用途"],
                 "mechanismSentences":["这是机制。这是边界。"],"caveats":[]}
                """, StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PLACEHOLDER_ELLIPSIS");

        assertRejectedWithDiagnostic(
                new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["**。这里是定义。这里是用途。"],
                 "mechanismSentences":["这是机制。这是边界。"],"caveats":[]}
                """, StructuredOutputValidationException.Reason
                        .DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_PRESENTATION_MARKDOWN_MARKER");

        JsonNode quotedCanonical = new GeneralProviderDraftCompiler(
                explanation()).compile(mapper.readTree("""
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["这里是定义。这里是用途。”"],
                 "mechanismSentences":["这是机制。这是边界。"],"caveats":[]}
                """));
        assertThat(quotedCanonical.path("statements").get(0)
                .path("text").textValue())
                .isEqualTo("这里是定义。这里是用途”。");
        assertCanonicalAndSemantic(explanation(), quotedCanonical);
    }

    @Test
    void rejectsMissingCaveatsOrSentenceArraysInsteadOfDefaultingThem()
            throws Exception {
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                explanation());

        assertRejected(compiler, """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["定义内容","典型用途"],
                 "mechanismSentences":["运行机制","适用边界"]}
                """, StructuredOutputValidationException.Reason.DRAFT_REQUIRED_FIELD_MISSING);
        assertRejected(compiler, """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "mechanismSentences":["运行机制","适用边界"],
                 "caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_REQUIRED_FIELD_MISSING);
    }

    @Test
    void rejectsMixedBranchesAndKindMismatchInsteadOfRepairingThem() throws Exception {
        assertRejected(new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["定义内容","典型用途"],
                 "mechanismSentences":["运行机制","适用边界"],
                 "comparisonSentences":["不应存在"],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        assertRejected(new GeneralProviderDraftCompiler(explanation()), """
                {"kind":"COMPARISON","comparisonSentences":["错误分支"],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_BRANCH_INVALID);
    }

    @Test
    void rejectsDepthEchoOrSentenceCountThatDisagreesWithTrustedRequest()
            throws Exception {
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                explanation());

        assertRejected(compiler, """
                {"kind":"EXPLANATION","depth":"CONCISE",
                 "definitionSentences":["定义内容"],
                 "mechanismSentences":["运行机制"],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
        assertRejectedWithDiagnostic(compiler, """
                {"kind":"EXPLANATION","depth":"STANDARD",
                 "definitionSentences":["定义内容"],
                 "mechanismSentences":["运行机制"],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SENTENCE_COUNT");
    }

    @Test
    void rejectsComparisonTextCountThatDoesNotMatchTrustedRequest()
            throws Exception {
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                comparison(List.of("A", "B"), Set.of("MECHANISM")));

        assertRejected(compiler, """
                {"kind":"COMPARISON","comparisonSentences":[
                  {"text":"只有一条","dimension":"MECHANISM","subjectIndex":1}
                ],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_REQUIRED_FIELD_MISSING);
        assertRejected(compiler, """
                {"kind":"COMPARISON","comparisonSentences":[
                  {"text":"第一条","dimension":"MECHANISM","subjectIndex":1},
                  {"text":"第二条","dimension":"MECHANISM","subjectIndex":2},
                  {"text":"多余一条","dimension":"MECHANISM","subjectIndex":1}
                ],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
    }

    @Test
    void rejectsDuplicatePairEvenWhenCountsMatchTrustedRequest()
            throws Exception {
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                comparison(List.of("A", "B"), Set.of("MECHANISM")));

        assertRejected(compiler, """
                {"kind":"COMPARISON","comparisonSentences":[
                  {"text":"第一次声明","dimension":"MECHANISM","subjectIndex":1},
                  {"text":"重复同对","dimension":"MECHANISM","subjectIndex":1}
                ],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_FIELD_CONFLICT);
    }

    @Test
    void rejectsDeclaredPairIdentityOutsideTrustedRequest()
            throws Exception {
        GeneralProviderDraftCompiler compiler = new GeneralProviderDraftCompiler(
                comparison(List.of("A", "B"), Set.of("MECHANISM")));

        assertRejectedWithDiagnostic(compiler, """
                {"kind":"COMPARISON","comparisonSentences":[
                  {"text":"主体甲正文","dimension":"MECHANISM","subjectIndex":1},
                  {"text":"未知维度正文","dimension":"MEMORY_MODEL","subjectIndex":2}
                ],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_DIMENSION");
        assertRejectedWithDiagnostic(compiler, """
                {"kind":"COMPARISON","comparisonSentences":[
                  {"text":"越界序号正文","dimension":"MECHANISM","subjectIndex":3},
                  {"text":"正常正文","dimension":"MECHANISM","subjectIndex":1}
                ],"caveats":[]}
                """, StructuredOutputValidationException.Reason.DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE,
                "DRAFT_VALUE_OUTSIDE_ALLOWED_SCOPE_SUBJECT_INDEX");
    }

    private void assertCanonicalAndSemantic(
            GeneralKnowledgeRequest request, JsonNode canonical) {
        JsonNode validated = registry.validateTree(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v2"),
                canonical).jsonTree();
        GeneralSemanticResult result = validator.validate(
                request, codec.decode(validated));
        assertThat(result.getContentVersion())
                .isEqualTo(request.getExpectedContentVersion());
    }

    private JsonNode providerDraft(String raw) {
        return registry.validate(
                new StructuredContractRef(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        "general.provider-draft.v3"),
                raw).jsonTree();
    }

    private void assertRejected(
            GeneralProviderDraftCompiler compiler, String draft,
            StructuredOutputValidationException.Reason reason) throws Exception {
        assertThatThrownBy(() -> compiler.compile(mapper.readTree(draft)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .extracting("reason")
                .isEqualTo(reason);
    }

    private void assertRejectedWithDiagnostic(
            GeneralProviderDraftCompiler compiler, String draft,
            StructuredOutputValidationException.Reason reason,
            String diagnosticReason) throws Exception {
        assertThatThrownBy(() -> compiler.compile(mapper.readTree(draft)))
                .isInstanceOf(StructuredOutputValidationException.class)
                .satisfies(failure -> {
                    StructuredOutputValidationException validation =
                            (StructuredOutputValidationException) failure;
                    assertThat(validation.getReason()).isEqualTo(reason);
                    assertThat(validation.getDiagnosticReason())
                            .isEqualTo(diagnosticReason);
                });
    }

    private GeneralKnowledgeRequest explanation() {
        return GeneralKnowledgeRequest.explanation(
                "并发控制", UserGoalProposal.Depth.STANDARD,
                GeneralKnowledgeRequest.Audience.GUEST, "public-1", deadline());
    }

    private GeneralKnowledgeRequest comparison(
            List<String> subjects, Set<String> dimensions) {
        return GeneralKnowledgeRequest.comparison(
                subjects, dimensions, GeneralKnowledgeRequest.Audience.GUEST,
                "public-1", deadline());
    }

    private TurnDeadline deadline() {
        return TurnDeadline.after(Duration.ofSeconds(5), Clock.systemUTC());
    }
}
