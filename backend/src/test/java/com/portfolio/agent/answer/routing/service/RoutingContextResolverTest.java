package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticContext;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.SubjectBindingRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingContextResolverTest {

    private static final String CONTENT_VERSION = "public-v1";

    private final RoutingContextResolver resolver = new RoutingContextResolver(new LegacySemanticContextAdapter());
    private final PublicSubjectCatalog catalog = new PublicSubjectCatalog(List.of(
            new PublicSubjectCatalog.Subject(SubjectType.PROJECT, "project-a", CONTENT_VERSION,
                    Set.of("项目A", "project a")),
            new PublicSubjectCatalog.Subject(SubjectType.PROJECT, "project-b", CONTENT_VERSION,
                    Set.of("项目B", "project b")),
            new PublicSubjectCatalog.Subject(SubjectType.CASE, "case-a", CONTENT_VERSION,
                    Set.of("案例A", "case a"))));

    @Test
    void explicitResultReferenceWinsOverLowerPrioritySources() {
        SubjectReference resultSubject = reference(
                SubjectType.PROJECT, "project-a", SubjectResolutionSource.STRUCTURED_RESULT);
        SubjectReference explicitSubject = reference(
                SubjectType.PROJECT, "project-b", SubjectResolutionSource.EXPLICIT_REFERENCE);
        SemanticTurnInput input = new SemanticTurnInput(
                "比较项目B", null, null, List.of(resultSubject), List.of(explicitSubject), List.of());

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectResolutionSource.STRUCTURED_RESULT, result.getResolutionSource());
        assertEquals(List.of(resultSubject), result.getSubjects());
    }

    @Test
    void explicitSubjectReferenceWinsOverQuestionText() {
        SubjectReference explicitSubject = reference(
                SubjectType.PROJECT, "project-a", SubjectResolutionSource.EXPLICIT_REFERENCE);
        SemanticTurnInput input = new SemanticTurnInput(
                "请介绍项目B", null, null, List.of(), List.of(explicitSubject), List.of());

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectResolutionSource.EXPLICIT_REFERENCE, result.getResolutionSource());
        assertEquals(List.of(explicitSubject), result.getSubjects());
    }

    @Test
    void uniqueCurrentQuestionCatalogMatchIsResolvedWithCatalogVersion() {
        SemanticTurnInput input = SemanticTurnInput.ask("请介绍 项目A 的实现");

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectResolutionSource.EXPLICIT_TEXT, result.getResolutionSource());
        assertEquals("project-a", result.getSubjects().get(0).getSubjectId());
        assertEquals(CONTENT_VERSION, result.getSubjects().get(0).getContentVersion());
    }

    @Test
    void pageSubjectIsOnlyHintForAnOrdinaryGeneralQuestion() {
        SemanticTurnInput input = new SemanticTurnInput(
                "什么是乐观锁？", null, null, List.of(), List.of(),
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.PAGE_CONTEXT)));

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.UNRESOLVED, result.getStatus());
        assertEquals(SubjectBindingRole.HINT, result.getBindings().get(0).getRole());
        assertEquals(List.of(), result.getSubjects());
    }

    @Test
    void deicticQuestionPromotesTheUniquePageHintToDeicticBinding() {
        SemanticTurnInput input = new SemanticTurnInput(
                "这个项目用了什么并发控制？", null, null, List.of(), List.of(),
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.PAGE_CONTEXT)));

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectBindingRole.DEICTIC, result.getBindings().get(0).getRole());
        assertEquals("project-a", result.getSubjects().get(0).getSubjectId());
    }

    @Test
    void ordinaryQuestionDoesNotInheritAnActiveContext() {
        SemanticContext context = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(), null, null, null, Set.of());

        ResolvedRoutingContext result = resolver.resolve(
                new SemanticTurnInput("解释乐观锁", context, null, List.of(), List.of(), List.of()), catalog);

        assertEquals(RoutingContextStatus.UNRESOLVED, result.getStatus());
        assertEquals(List.of(), result.getSubjects());
    }

    @Test
    void comparisonQuestionResolvesEveryExplicitlyNamedSubject() {
        SemanticTurnInput input = SemanticTurnInput.ask("比较项目A和项目B");

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectResolutionSource.EXPLICIT_TEXT, result.getResolutionSource());
        assertEquals(List.of("project-a", "project-b"), result.getSubjects().stream()
                .map(SubjectReference::getSubjectId)
                .toList());
    }

    @Test
    void multipleBareQuestionMatchesRemainAmbiguous() {
        SemanticTurnInput input = SemanticTurnInput.ask("项目A 项目B");

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.AMBIGUOUS, result.getStatus());
        assertEquals("ROUTING_SUBJECT_AMBIGUOUS", result.getReasonCode());
        assertEquals(List.of(), result.getSubjects());
    }

    @Test
    void activeStructuredSubjectDisambiguatesDuplicateQuestionTitle() {
        PublicSubjectCatalog titledCatalog = new PublicSubjectCatalog(List.of(
                new PublicSubjectCatalog.Subject(
                        SubjectType.PROJECT, "project-a", CONTENT_VERSION, Set.of("shared-title")),
                new PublicSubjectCatalog.Subject(
                        SubjectType.CASE, "case-a", CONTENT_VERSION, Set.of("shared-title"))));
        SemanticContext context = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(), null, null, null, Set.of());

        ResolvedRoutingContext result = resolver.resolve(
                new SemanticTurnInput("shared-title", context, null, List.of(), List.of(), List.of()),
                titledCatalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals("project-a", result.getSubjects().get(0).getSubjectId());
        assertEquals(SubjectResolutionSource.ACTIVE_SUBJECT, result.getResolutionSource());
    }

    @Test
    void noCatalogMatchIsUnresolvedAndDoesNotInferFromAssistantMessages() {
        SemanticTurnInput input = SemanticTurnInput.ask("继续比较它们");

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.UNRESOLVED, result.getStatus());
        assertEquals("ROUTING_SUBJECT_UNRESOLVED", result.getReasonCode());
        assertFalse(hasConversationMessageAccessor());
    }

    @Test
    void conflictingLegacyAndSemanticSubjectContextIsInvalidInput() {
        SemanticContext semanticContext = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(), null, null, null, Set.of());
        LegacySemanticContextAdapter.LegacyContext legacyContext =
                LegacySemanticContextAdapter.LegacyContext.of(
                        "project-b", null, List.of(), List.of(), null, null, Set.of(), CONTENT_VERSION);
        SemanticTurnInput input = new SemanticTurnInput(
                "继续", semanticContext, legacyContext, List.of(), List.of(), List.of());

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.INVALID_INPUT, result.getStatus());
        assertEquals("ROUTING_CONTEXT_CONFLICT", result.getReasonCode());
    }

    @Test
    void resolvedContextCollectionsAreImmutableAndDiagnosticsDoNotContainSubjectIds() {
        SemanticTurnInput input = new SemanticTurnInput(
                "继续", null, null, List.of(), List.of(),
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.PAGE_CONTEXT)));

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertThrows(UnsupportedOperationException.class,
                () -> result.getSubjects().add(reference(
                        SubjectType.PROJECT, "project-b", SubjectResolutionSource.PAGE_CONTEXT)));
        assertFalse(result.toString().contains("project-a"));
    }

    @Test
    void resolvesPendingPlanBeforeRecentResultsPageAndActiveSubjects() {
        SemanticContext context = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(reference(SubjectType.PROJECT, "project-b", SubjectResolutionSource.STRUCTURED_RESULT)),
                new SemanticContext.PendingPlanReference(
                        "pending-plan", List.of(reference(
                                SubjectType.PROJECT, "project-a", SubjectResolutionSource.PENDING_PLAN))),
                null, null, Set.of());
        SemanticTurnInput input = new SemanticTurnInput(
                "继续基于这个结果", context, null, List.of(), List.of(),
                List.of(reference(SubjectType.CASE, "case-a", SubjectResolutionSource.PAGE_CONTEXT)));

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectResolutionSource.PENDING_PLAN, result.getResolutionSource());
        assertEquals("project-a", result.getSubjects().get(0).getSubjectId());
    }

    @Test
    void resolvesRecentResultBeforePageAndActiveSubjects() {
        SemanticContext context = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(reference(SubjectType.PROJECT, "project-b", SubjectResolutionSource.STRUCTURED_RESULT)),
                null, null, null, Set.of());
        SemanticTurnInput input = new SemanticTurnInput(
                "继续基于这个结果", context, null, List.of(), List.of(),
                List.of(reference(SubjectType.CASE, "case-a", SubjectResolutionSource.PAGE_CONTEXT)));

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(SubjectResolutionSource.STRUCTURED_RESULT, result.getResolutionSource());
        assertEquals("project-b", result.getSubjects().get(0).getSubjectId());
    }

    @Test
    void bareContinuationDoesNotGuessWhichRecentResultToReuse() {
        SemanticContext context = SemanticContext.of(
                List.of(),
                List.of(reference(SubjectType.PROJECT, "project-b", SubjectResolutionSource.STRUCTURED_RESULT)),
                null, null, null, Set.of());

        ResolvedRoutingContext result = resolver.resolve(
                new SemanticTurnInput("继续", context, null, List.of(), List.of(), List.of()), catalog);

        assertEquals(RoutingContextStatus.UNRESOLVED, result.getStatus());
        assertEquals("ROUTING_SUBJECT_UNRESOLVED", result.getReasonCode());
    }

    @Test
    void keepsPageSubjectAsHintForAnOrdinaryContinuation() {
        SemanticContext context = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(), null, null, null, Set.of());
        SemanticTurnInput input = new SemanticTurnInput(
                "继续", context, null, List.of(), List.of(),
                List.of(reference(SubjectType.CASE, "case-a", SubjectResolutionSource.PAGE_CONTEXT)));

        ResolvedRoutingContext result = resolver.resolve(input, catalog);

        assertEquals(RoutingContextStatus.UNRESOLVED, result.getStatus());
        assertEquals(SubjectBindingRole.HINT, result.getBindings().get(0).getRole());
        assertEquals(List.of(), result.getSubjects());
    }

    @Test
    void doesNotBindUniqueActiveSubjectWithoutAContextDemand() {
        SemanticContext context = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(), null, null, null, Set.of());

        ResolvedRoutingContext result = resolver.resolve(
                new SemanticTurnInput("继续", context, null, List.of(), List.of(), List.of()), catalog);

        assertEquals(RoutingContextStatus.UNRESOLVED, result.getStatus());
        assertEquals(List.of(), result.getSubjects());
    }

    @Test
    void acceptsOnlyOneCatalogValidatedModelCandidateAfterDeterministicSources() {
        ResolvedRoutingContext unresolved = resolver.resolve(SemanticTurnInput.ask("继续"), catalog);

        ResolvedRoutingContext result = resolver.resolveValidatedModelCandidates(
                unresolved,
                List.of(reference(SubjectType.PROJECT, "project-b", SubjectResolutionSource.EXPLICIT_TEXT)),
                catalog);

        assertEquals(RoutingContextStatus.RESOLVED, result.getStatus());
        assertEquals(SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE, result.getResolutionSource());
        assertEquals("project-b", result.getSubjects().get(0).getSubjectId());
    }

    @Test
    void validatesReviewedSlugAliasAndNormalizesToStableSubjectId() {
        PublicSubjectCatalog reviewedCatalog = new PublicSubjectCatalog(List.of(
                new PublicSubjectCatalog.Subject(
                        SubjectType.PROJECT,
                        "project-stable-id",
                        CONTENT_VERSION,
                        Set.of("sql-audit", "SQL Audit"))));

        Optional<SubjectReference> resolved = reviewedCatalog.validate(
                reference(SubjectType.PROJECT, "sql-audit", SubjectResolutionSource.EXPLICIT_REFERENCE),
                SubjectResolutionSource.EXPLICIT_REFERENCE);

        assertEquals("project-stable-id", resolved.orElseThrow().getSubjectId());
        assertEquals(CONTENT_VERSION, resolved.orElseThrow().getContentVersion());
    }

    @Test
    void acceptsDuplicateAliasesForTheSameCanonicalSubject() {
        PublicSubjectCatalog reviewedCatalog = new PublicSubjectCatalog(List.of(
                new PublicSubjectCatalog.Subject(
                        SubjectType.PROJECT,
                        "project-stable-id",
                        CONTENT_VERSION,
                        Set.of("SQL Audit", "sql-audit"))));

        Optional<SubjectReference> resolved = reviewedCatalog.validate(
                reference(SubjectType.PROJECT, "SQL AUDIT", SubjectResolutionSource.EXPLICIT_REFERENCE),
                SubjectResolutionSource.EXPLICIT_REFERENCE);

        assertEquals("project-stable-id", resolved.orElseThrow().getSubjectId());
    }

    @Test
    void rejectsConflictingReviewedAliasesFailClosed() {
        PublicSubjectCatalog reviewedCatalog = new PublicSubjectCatalog(List.of(
                new PublicSubjectCatalog.Subject(
                        SubjectType.PROJECT,
                        "project-a",
                        CONTENT_VERSION,
                        Set.of("shared-slug")),
                new PublicSubjectCatalog.Subject(
                        SubjectType.PROJECT,
                        "project-b",
                        CONTENT_VERSION,
                        Set.of("SHARED-SLUG"))));

        assertFalse(reviewedCatalog.validate(
                reference(SubjectType.PROJECT, "shared-slug", SubjectResolutionSource.EXPLICIT_REFERENCE),
                SubjectResolutionSource.EXPLICIT_REFERENCE).isPresent());
    }

    @Test
    void rejectsCrossTypeAliasCollisionInsteadOfGuessingSubjectType() {
        PublicSubjectCatalog reviewedCatalog = new PublicSubjectCatalog(List.of(
                new PublicSubjectCatalog.Subject(
                        SubjectType.PROJECT,
                        "project-a",
                        CONTENT_VERSION,
                        Set.of("shared-title")),
                new PublicSubjectCatalog.Subject(
                        SubjectType.CASE,
                        "case-a",
                        CONTENT_VERSION,
                        Set.of("shared-title"))));

        assertFalse(reviewedCatalog.validate(
                reference(SubjectType.CASE, "shared-title", SubjectResolutionSource.EXPLICIT_REFERENCE),
                SubjectResolutionSource.EXPLICIT_REFERENCE).isPresent());
    }

    @Test
    void ambiguousDeterministicResolutionCannotBeOverriddenByModelCandidate() {
        ResolvedRoutingContext ambiguous = resolver.resolve(
                SemanticTurnInput.ask("project-a project-b"), catalog);

        ResolvedRoutingContext result = resolver.resolveValidatedModelCandidates(
                ambiguous,
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.EXPLICIT_TEXT)),
                catalog);

        assertEquals(RoutingContextStatus.AMBIGUOUS, result.getStatus());
        assertEquals("ROUTING_SUBJECT_AMBIGUOUS", result.getReasonCode());
        assertEquals(List.of(), result.getSubjects());
    }

    @Test
    void actionAwareInputEnforcesQuestionAndSignedConfirmationInvariants() {
        PlanConfirmation.Submission submission = new PlanConfirmation.Submission(
                "confirm-1", "opaque-plan", "sha256:fingerprint", "opaque-token");
        SemanticTurnInput confirmation = SemanticTurnInput.confirmPlan("turn-confirm", submission);
        SemanticContext currentContext = SemanticContext.of(
                List.of(reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)),
                List.of(), null, null, null, Set.of());
        SemanticTurnInput regenerate = SemanticTurnInput.regeneratePlan(
                "turn-regenerate", "重新生成计划", new SemanticTurnInput.InvalidatedPlanReference(
                        "plan-1", "sha256:fingerprint"), currentContext);

        assertEquals(SemanticTurnInput.Action.CONFIRM_PLAN, confirmation.getAction());
        assertEquals(submission.getConfirmationId(), confirmation.getConfirmationSubmission().getConfirmationId());
        assertEquals(SemanticTurnInput.Action.REGENERATE_PLAN, regenerate.getAction());
        assertEquals(currentContext, regenerate.getSemanticContext());
        assertThrows(IllegalArgumentException.class, () -> SemanticTurnInput.regeneratePlan(
                "turn-regenerate", "regenerate", new SemanticTurnInput.InvalidatedPlanReference(
                        "plan-1", "sha256:fingerprint"), null));
        assertThrows(IllegalArgumentException.class, () -> new SemanticTurnInput(
                "turn-confirm", SemanticTurnInput.Action.CONFIRM_PLAN, "不得有问题", null, null,
                List.of(), List.of(), List.of(), submission, null, null, "stp-v1", null, null));
        assertThrows(IllegalArgumentException.class, () -> SemanticTurnInput.ask(null));
    }

    @Test
    void contextAndInputCopyCollectionsAndHaveValueSemantics() {
        List<SubjectReference> active = new ArrayList<>(List.of(
                reference(SubjectType.PROJECT, "project-a", SubjectResolutionSource.ACTIVE_SUBJECT)));
        Set<String> topics = new HashSet<>(Set.of("implementation"));
        SemanticContext first = SemanticContext.of(active, List.of(), null, "INTERVIEWER", "PAGE", topics);
        SemanticContext second = SemanticContext.of(
                List.of(active.get(0)), List.of(), null, "INTERVIEWER", "PAGE", Set.of("implementation"));
        active.clear();
        topics.add("leak");

        assertEquals(second, first);
        assertEquals(second.hashCode(), first.hashCode());
        assertEquals(1, first.getActiveSubjects().size());
        assertThrows(UnsupportedOperationException.class,
                () -> first.getCoveredTopics().add("mutate"));

        SemanticTurnInput input = new SemanticTurnInput(
                "问句", first, null, List.of(), List.of(), List.of());
        assertEquals(input, new SemanticTurnInput(
                "问句", second, null, List.of(), List.of(), List.of()));
        assertFalse(input.toString().contains("问句"));
    }

    private SubjectReference reference(
            SubjectType subjectType,
            String subjectId,
            SubjectResolutionSource resolutionSource) {
        return new SubjectReference(subjectType, subjectId, resolutionSource, CONTENT_VERSION);
    }

    private boolean hasConversationMessageAccessor() {
        for (Method method : SemanticTurnInput.class.getDeclaredMethods()) {
            if ("getMessages".equals(method.getName())) {
                return true;
            }
        }
        return false;
    }
}
