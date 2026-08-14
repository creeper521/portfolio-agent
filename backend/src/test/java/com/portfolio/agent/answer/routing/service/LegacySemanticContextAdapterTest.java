package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioFollowUpAction;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacySemanticContextAdapterTest {

    private static final String CONTENT_VERSION = "public-v1";

    private final LegacySemanticContextAdapter adapter = new LegacySemanticContextAdapter();

    @Test
    void adaptsLegacyProjectReferenceRecommendationAndRequestMetadata() {
        LegacySemanticContextAdapter.LegacyContext legacy = LegacySemanticContextAdapter.LegacyContext.of(
                "project-a", null,
                List.of("project-b"), List.of("project-a"),
                "INTERVIEWER", "PROJECT_DETAIL", Set.of("IMPLEMENTATION"), CONTENT_VERSION);

        SemanticContext result = adapter.adapt(legacy);

        assertEquals(1, result.getActiveSubjects().size());
        assertEquals(2, result.getResultReferences().size());
        assertEquals(SubjectResolutionSource.ACTIVE_SUBJECT,
                result.getActiveSubjects().get(0).getResolutionSource());
        assertEquals("INTERVIEWER", result.getAudienceRole());
        assertEquals("PROJECT_DETAIL", result.getRequestSource());
    }

    @Test
    void rejectsLegacyProjectAndCaseConflict() {
        LegacySemanticContextAdapter.LegacyContext legacy = LegacySemanticContextAdapter.LegacyContext.of(
                "project-a", "case-a", List.of(), List.of(), null, null, Set.of(), CONTENT_VERSION);

        assertThrows(IllegalArgumentException.class, () -> adapter.adapt(legacy));
    }

    @Test
    void mergeAcceptsEquivalentSemanticAndLegacyContexts() {
        SubjectReference active = new SubjectReference(
                SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT, CONTENT_VERSION);
        SemanticContext semantic = SemanticContext.of(
                List.of(active), List.of(), null, "INTERVIEWER", "PROJECT_DETAIL", Set.of("IMPLEMENTATION"));
        LegacySemanticContextAdapter.LegacyContext legacy = LegacySemanticContextAdapter.LegacyContext.of(
                "project-a", null, List.of(), List.of(),
                "INTERVIEWER", "PROJECT_DETAIL", Set.of("IMPLEMENTATION"), CONTENT_VERSION);

        LegacySemanticContextAdapter.ContextMergeResult result = adapter.merge(semantic, legacy);

        assertFalse(result.isConflict());
        assertEquals(semantic, result.getContext());
    }

    @Test
    void mapsTypedLegacyCaseReferenceWithoutTurningItIntoAProject() {
        LegacySemanticContextAdapter.LegacyContext legacy =
                LegacySemanticContextAdapter.LegacyContext.ofWithTypedReferences(
                        null, null, List.of(), List.of(), List.of("case-a"),
                        null, "REFERENCE", Set.of(), CONTENT_VERSION);

        SemanticContext result = adapter.adapt(legacy);

        assertEquals(1, result.getResultReferences().size());
        assertEquals(SubjectType.CASE, result.getResultReferences().get(0).getSubjectType());
        assertEquals("case-a", result.getResultReferences().get(0).getSubjectId());
    }

    @Test
    void resolvesCaseOnlyLegacyReferenceAgainstCaseOnlyCatalog() {
        LegacySemanticContextAdapter.LegacyContext legacy =
                LegacySemanticContextAdapter.LegacyContext.ofWithTypedReferences(
                        null, null, List.of(), List.of(), List.of("case-a"),
                        null, "REFERENCE", Set.of(), CONTENT_VERSION);
        PublicSubjectCatalog catalog = new PublicSubjectCatalog(List.of(
                new PublicSubjectCatalog.Subject(
                        SubjectType.CASE, "case-a", CONTENT_VERSION, Set.of("case a"))));

        ResolvedRoutingContext result = new RoutingContextResolver(adapter).resolve(
                new com.portfolio.agent.answer.routing.domain.SemanticTurnInput(
                        "继续基于这个结果", null, legacy, List.of(), List.of(), List.of()), catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectType.CASE, result.getSubjects().get(0).getSubjectType());
    }

    @Test
    void adaptsTheRealLegacyPortfolioTurnReferenceContextWithCaseType() {
        PortfolioReferenceContext reference = new PortfolioReferenceContext(
                CONTENT_VERSION, List.of(), List.of("case-a"), null, List.of(), null,
                PortfolioFollowUpAction.SHOW_EVIDENCE);
        PortfolioTurn legacyTurn = PortfolioTurn.builder("turn-legacy", "继续查看案例")
                .referenceContext(reference)
                .source("REFERENCE")
                .build();

        SemanticContext result = adapter.adapt(legacyTurn);

        assertEquals(SubjectType.CASE, result.getResultReferences().get(0).getSubjectType());
        assertEquals(CONTENT_VERSION, result.getResultReferences().get(0).getContentVersion());
    }

    @Test
    void legacyContextIsImmutableValueWithRedactedDiagnostics() {
        List<String> references = new ArrayList<>(List.of("project-a"));
        Set<String> topics = new HashSet<>(Set.of("implementation"));
        LegacySemanticContextAdapter.LegacyContext first = LegacySemanticContextAdapter.LegacyContext.of(
                null, null, List.of(), references, "INTERVIEWER", "PAGE", topics, CONTENT_VERSION);
        LegacySemanticContextAdapter.LegacyContext second = LegacySemanticContextAdapter.LegacyContext.of(
                null, null, List.of(), List.of("project-a"), "INTERVIEWER", "PAGE",
                Set.of("implementation"), CONTENT_VERSION);
        references.clear();
        topics.add("leak");

        assertEquals(second, first);
        assertEquals(second.hashCode(), first.hashCode());
        assertEquals(1, first.getReferenceProjectIds().size());
        assertThrows(UnsupportedOperationException.class,
                () -> first.getReferenceProjectIds().add("project-b"));
        assertFalse(first.toString().contains("project-a"));
    }

}
