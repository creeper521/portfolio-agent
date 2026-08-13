package com.portfolio.agent.answer.composition.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.OrderingPolicy;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PresentationRole;
import com.portfolio.agent.answer.composition.domain.RequestedFacet;
import com.portfolio.agent.answer.composition.domain.RequestedOutput;
import com.portfolio.agent.answer.composition.domain.ResponseDepth;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.composition.domain.SummaryPolicy;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import com.portfolio.agent.answer.composition.validation.ProtectedAtomExtractorTest;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExpressionInputPrivacyTest {
    private static final List<String> FORBIDDEN_FIELD_FRAGMENTS = List.of(
            "question", "questionspan", "goallabel", "history", "message", "context",
            "token", "conversation", "subjectid", "claimid", "evidenceid", "chunkid",
            "route", "referencekey", "publishedversion", "score", "exception", "provider");

    @Test void closedIntentCannotRepresentRawQuestionHistoryContextTokensOrIds() {
        for (Field field : ExpressionIntent.class.getDeclaredFields()) {
            String normalized = field.getName().toLowerCase();
            assertThat(FORBIDDEN_FIELD_FRAGMENTS)
                    .noneMatch(normalized::contains);
        }
        for (Field field : PortfolioCompositionContext.class.getDeclaredFields()) {
            assertThat(field.getType()).isIn(ExpressionIntent.class, ExpressionAllowance.class);
        }
    }

    @Test void serializedInputUsesAnExactWhitelistAndExcludesServerOnlySentinels() throws Exception {
        GroundedStatement statement = ProtectedAtomExtractorTest.statement(
                "公开项目使用 PostgreSQL v16 完成公开验证");
        ExpressionStatement entry = new ExpressionStatement(statement, PresentationRole.REQUIRED,
                AnswerSectionType.VERIFICATION, 0);
        FactAnswerMaterial material = new FactAnswerMaterial("GOAL_LABEL_SENTINEL",
                new SubjectReference("公开项目"), FocusMode.FOCUSED,
                List.of(new FactAnswerMaterial.FactSection(AnswerSectionType.VERIFICATION,
                        List.of(entry), OrderingPolicy.STABLE)), SummaryPolicy.FORBIDDEN,
                List.of("QUESTION_SPAN_HISTORY_CONTEXT_SENTINEL"),
                List.of("CHUNK_EVIDENCE_SENTINEL"));
        ExpressionIntent intent = new ExpressionIntent(TaskKind.FACT, FocusMode.FOCUSED,
                List.of(RequestedFacet.VERIFICATION), List.of(),
                List.of(RequestedOutput.DIRECT_ANSWER), AudienceRole.INTERVIEWER,
                ResponseDepth.MEDIUM, LocaleCode.ZH_CN, TaskSource.FREE_TEXT,
                List.of("公开项目"));
        PortfolioCompositionContext context = new PortfolioCompositionContext(intent,
                new ExpressionAllowance(true, Instant.parse("2026-08-13T00:00:00Z"), 1800, 16, 99));
        ExpressionInputDocument document = new ModelExpressionInputProjector().project(material, context);
        String json = document.getSerializedJson();

        assertThat(json).doesNotContain("GOAL_LABEL_SENTINEL",
                "QUESTION_SPAN_HISTORY_CONTEXT_SENTINEL", "CHUNK_EVIDENCE_SENTINEL",
                "REF-1", "/projects/public", "/evidence/public", "2026-08-13T00:00:00Z",
                "\"requestLocalAttemptOrdinal\"", "\"title\"", "\"caveats\"");

        JsonNode root = new ObjectMapper().readTree(json);
        assertThat(fieldNames(root)).containsExactlyInAnyOrder(
                "schemaVersion", "materialKind", "intent", "shape", "subjects", "statements");
        assertThat(fieldNames(root.get("intent"))).containsExactlyInAnyOrder(
                "taskKind", "focusMode", "requestedFacets", "requestedDimensions",
                "requestedOutputs", "audienceRole", "responseDepth", "locale", "taskSource");
        assertThat(fieldNames(root.get("subjects").get(0))).containsExactlyInAnyOrder("key", "label");
        assertThat(fieldNames(root.get("statements").get(0))).containsExactlyInAnyOrder(
                "key", "role", "section", "subjectKey", "predicate", "statement", "detail",
                "claimCategory", "achievementStatus", "contributionType", "verificationBasis",
                "materiality", "supportTarget");
        assertThat(document.getAliases().toString()).doesNotContain("公开项目", "REF-1");
    }

    private static Set<String> fieldNames(JsonNode node) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
