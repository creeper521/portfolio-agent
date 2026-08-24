package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnLifecycleContinuationTest {
    @Test void askFreeTextUsesServerDiscussionContextBeforeStandardResolution() {
        ActiveFixture fixture = activeFixture(
                com.portfolio.agent.turn.planning.GoalInterpretationResult
                        .conversational("谢谢"));

        AgentTurnLifecycleService.Result result = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.Ask(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("谢谢"), null, null));

        assertThat(result.turn())
                .isInstanceOf(PublicAgentTurn.Conversational.class);
        org.mockito.Mockito.verify(fixture.resolver())
                .interpretTyped(any(), any(), any());
        org.mockito.Mockito.verify(fixture.resolver(), org.mockito.Mockito.never())
                .resolve(any(), any(), any(), any());
    }

    @Test void interpretationFailureDoesNotPersistOriginalTextInSuggestedAction() {
        ActiveFixture fixture = activeFixture(
                com.portfolio.agent.turn.planning.GoalInterpretationResult
                        .conversational("unused"));
        when(fixture.resolver().interpretTyped(any(), any(), any())).thenThrow(
                new com.portfolio.agent.turn.planning
                        .GoalInterpretationUnavailableException());

        AgentTurnLifecycleService.Result result = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.Ask(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("继续说明验证方式"),
                        null, null));

        PublicAgentTurn.CapabilityUnavailable unavailable =
                (PublicAgentTurn.CapabilityUnavailable) result.turn();
        assertThat(unavailable.getCode())
                .isEqualTo("DISCUSSION_INTERPRETATION_UNAVAILABLE");
        assertThat(unavailable.isRetryable()).isTrue();
        assertThat(unavailable.getSuggestedActions())
                .extracting(com.portfolio.agent.turn.projection
                        .SuggestedAction::getActionId)
                .containsExactly("discussion-exit");
        assertThat(unavailable.getSuggestedActions().getFirst().getInputText())
                .isNull();
        assertThat(unavailable.getSuggestedActions().getFirst()
                .getContinuation().getOperation())
                .isEqualTo(com.portfolio.agent.turn.continuation
                        .ContinuationReference.Operation.EXIT_CONTEXT);
    }

    @Test void discussionUncertaintyCreatesClosedFacetClarificationAndGuardsPointer() {
        ActiveFixture fixture = activeFixture(
                com.portfolio.agent.turn.planning.GoalInterpretationResult.semanticRoute(
                        com.portfolio.agent.turn.planning.SemanticRouteProposal
                                .needsClarification()));

        AgentTurnLifecycleService.Result result = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.Ask(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("再说详细一点"), null, null));

        PublicAgentTurn.Clarification clarification =
                (PublicAgentTurn.Clarification) result.turn();
        com.portfolio.agent.turn.continuation.ClarificationChallenge.SingleChoiceField field =
                (com.portfolio.agent.turn.continuation.ClarificationChallenge.SingleChoiceField)
                        clarification.getClarification().getFields().getFirst();
        assertThat(field.getChoices())
                .extracting(com.portfolio.agent.turn.continuation
                        .ClarificationChallenge.Choice::choiceId)
                .containsExactly(
                        "choice_facet_overview",
                        "choice_facet_responsibility",
                        "choice_facet_solution",
                        "choice_facet_verification",
                        "choice_facet_status");
        org.mockito.ArgumentCaptor<List<com.portfolio.agent.turn.continuation
                .ClarificationStore.Record>> records =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<com.portfolio.agent.turn.continuation
                .DiscussionStateMutation> mutation =
                org.mockito.ArgumentCaptor.forClass(
                        com.portfolio.agent.turn.continuation
                                .DiscussionStateMutation.class);
        org.mockito.Mockito.verify(fixture.store()).completeWithSession(
                any(), any(), any(), any(), records.capture(),
                any(), any(), any(), any(), mutation.capture(), any());
        assertThat(records.getValue()).singleElement().satisfies(record -> {
            assertThat(record.resumeTemplate()).isInstanceOf(
                    com.portfolio.agent.turn.planning
                            .DiscussionClarificationTemplate.class);
            assertThat(record.choiceBindings().values().stream()
                    .flatMap(value -> value.values().stream()).toList())
                    .containsExactlyInAnyOrder(
                            "discussion:facet:OVERVIEW",
                            "discussion:facet:RESPONSIBILITY",
                            "discussion:facet:SOLUTION",
                            "discussion:facet:VERIFICATION",
                            "discussion:facet:STATUS");
        });
        assertThat(mutation.getValue().getExpectedGeneration())
                .contains("discussion_active_123");
    }

    @Test void discussionFacetChoiceResumesAsDeterministicLockedGoal() {
        ActiveFixture fixture = activeFixture(
                com.portfolio.agent.turn.planning.GoalInterpretationResult.semanticRoute(
                        com.portfolio.agent.turn.planning.SemanticRouteProposal
                                .needsClarification()));
        AgentTurnLifecycleService.Result first = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.Ask(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("再说详细一点"), null, null));
        PublicAgentTurn.Clarification challengeTurn =
                (PublicAgentTurn.Clarification) first.turn();
        org.mockito.ArgumentCaptor<List<com.portfolio.agent.turn.continuation
                .ClarificationStore.Record>> records =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(fixture.store()).completeWithSession(
                any(), any(), any(), any(), records.capture(),
                any(), any(), any(), any(), any(), any());
        com.portfolio.agent.turn.continuation.ClarificationStore.Record record =
                records.getValue().getFirst();
        org.mockito.Mockito.clearInvocations(fixture.store(), fixture.compiler());
        when(fixture.store().reserveClarification(
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.portfolio.agent.turn.continuation
                        .ClarificationStore.ReserveResult(
                        com.portfolio.agent.turn.continuation
                                .ClarificationStore.Status.RESERVED,
                        record,
                        new com.portfolio.agent.turn.continuation
                                .ClarificationStore.ResolvedAnswer(
                                "field_discussion_direction",
                                "discussion:facet:SOLUTION", null),
                        0));
        when(fixture.compiler().compile(any(), any(), any(), any()))
                .thenReturn(com.portfolio.agent.turn.planning.PlanCompilationResult
                        .rejected("stop-after-resolution"));

        AgentTurnLifecycleService.Result resumed = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.ResolveClarification(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        challengeTurn.getClarification().getClarificationId(),
                        new AgentTurnCommand.ChoiceAnswer("choice_facet_solution"),
                        null, null));

        assertThat(resumed.turn()).isInstanceOf(PublicAgentTurn.Boundary.class);
        org.mockito.ArgumentCaptor<com.portfolio.agent.turn.planning.UserGoalProposal> goal =
                org.mockito.ArgumentCaptor.forClass(
                        com.portfolio.agent.turn.planning.UserGoalProposal.class);
        org.mockito.Mockito.verify(fixture.compiler())
                .compile(goal.capture(), any(), any(), any());
        com.portfolio.agent.turn.planning.UserGoalProposal.ProposedGoal proposed =
                goal.getValue().getGoals().getFirst();
        assertThat(proposed.getRequestedOutputs()).containsExactly(
                com.portfolio.agent.turn.planning.GoalRequestedOutput.SOLUTION);
        assertThat(proposed.getSubjectCandidates()).singleElement().satisfies(subject -> {
            assertThat(subject.getReference()).isEqualTo("project-a");
            assertThat(subject.getBasis()).isEqualTo(
                    com.portfolio.agent.turn.planning.GoalSubjectReference.Basis.CONTINUATION);
        });
    }

    @Test void expiredDiscussionUncertaintyOffersOnlyTypedReentry() {
        ActiveFixture fixture = activeFixture(
                com.portfolio.agent.turn.planning.GoalInterpretationResult.semanticRoute(
                        com.portfolio.agent.turn.planning.SemanticRouteProposal
                                .needsClarification()),
                LifecycleTestFixture.NOW.minusSeconds(1));

        AgentTurnLifecycleService.Result result = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.Ask(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("继续当前项目"), null, null));

        PublicAgentTurn.Clarification clarification =
                (PublicAgentTurn.Clarification) result.turn();
        com.portfolio.agent.turn.continuation.ClarificationChallenge.SingleChoiceField field =
                (com.portfolio.agent.turn.continuation.ClarificationChallenge.SingleChoiceField)
                        clarification.getClarification().getFields().getFirst();
        assertThat(field.getChoices())
                .extracting(com.portfolio.agent.turn.continuation
                        .ClarificationChallenge.Choice::choiceId)
                .containsExactly("choice_reenter_project");
        org.mockito.ArgumentCaptor<List<com.portfolio.agent.turn.continuation
                .ClarificationStore.Record>> records =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(fixture.store()).completeWithSession(
                any(), any(), any(), any(), records.capture(),
                any(), any(), any(), any(), any(), any());
        com.portfolio.agent.turn.planning.DiscussionClarificationTemplate template =
                (com.portfolio.agent.turn.planning.DiscussionClarificationTemplate)
                        records.getValue().getFirst().resumeTemplate();
        assertThat(template.isReenterAllowed()).isTrue();
        assertThat(template.getAllowedFacets()).isEmpty();
    }

    @Test void explicitReenterCannotReplaceAnActivePointer() {
        ActiveFixture fixture = activeFixture(
                com.portfolio.agent.turn.planning.GoalInterpretationResult
                        .conversational("unused"));

        AgentTurnLifecycleService.Result result = fixture.service().execute(
                fixture.token(), new AgentTurnCommand.Continue(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        AgentTurnCommand.ContinueOperation.REENTER_SUBJECT,
                        null, null, null,
                        new AgentTurnCommand.ContinueSubject(
                                AgentTurnCommand.ContinueSubjectKind.PROJECT,
                                "project-a"), null, null));

        PublicAgentTurn.CapabilityUnavailable unavailable =
                (PublicAgentTurn.CapabilityUnavailable) result.turn();
        assertThat(unavailable.getCode())
                .isEqualTo("DISCUSSION_CONTEXT_MISMATCH");
        org.mockito.Mockito.verifyNoInteractions(fixture.resolver());
    }

    @Test void singleCandidateStillClarifiesWhenTheModelIsUncertain() {
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        com.portfolio.agent.turn.continuation.ConversationSessionResolver sessionResolver =
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, new byte[32],
                        Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(30));
        UUID seedRequest = UUID.randomUUID();
        com.portfolio.agent.turn.continuation.ConversationSessionResolver.Resolution issued =
                sessionResolver.resolve(
                        null, seedRequest,
                        com.portfolio.agent.turn.execution.TurnDeadline.after(
                                Duration.ofSeconds(5),
                                Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC)));
        sessions.save(sessionResolver.pendingSession(issued));

        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.findContext(any(), any(), any(), any())).thenReturn(Optional.of(
                new com.portfolio.agent.turn.continuation.ContinuationContext.Recommendation(
                        "recommendation_handle_123", issued.conversationId(),
                        "public-1", LifecycleTestFixture.NOW.plus(Duration.ofMinutes(5)),
                        true, Set.of(), Set.of(), Set.of(), Set.of(), 1,
                        List.of(new com.portfolio.agent.turn.continuation.ContinuationContext.ResultItem(
                                "item-a", "project-a")))));
        when(store.completeWithSession(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(
                new TurnExecutionStore.SettlementResult(true, null));

        com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway knowledge =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway.class);
        com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent content =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent.class);
        com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge project =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge.class);
        when(project.getStableId()).thenReturn("project-a");
        when(project.getSlug()).thenReturn("project-a-slug");
        when(project.getTitle()).thenReturn("项目 A");
        when(content.getProjects()).thenReturn(List.of(project));
        when(content.getCases()).thenReturn(List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        com.portfolio.agent.turn.planning.GoalResolver resolver =
                mock(com.portfolio.agent.turn.planning.GoalResolver.class);
        when(resolver.interpretTyped(any(), any(), any())).thenReturn(
                com.portfolio.agent.turn.planning.GoalInterpretationResult.semanticRoute(
                        com.portfolio.agent.turn.planning.SemanticRouteProposal.needsClarification()));
        com.portfolio.agent.turn.planning.SemanticPlanCompiler compiler =
                mock(com.portfolio.agent.turn.planning.SemanticPlanCompiler.class);
        AgentTurnLifecycleService service = new AgentTurnLifecycleService(
                knowledge, resolver,
                compiler,
                mock(com.portfolio.agent.turn.execution.SemanticTurnEngine.class),
                new com.portfolio.agent.turn.projection.PublicAgentTurnProjector(),
                new com.portfolio.agent.turn.continuation.ContextMutationPlanner(
                        () -> "context_handle_123"),
                store, new RequestFingerprintFactory(new byte[32]),
                sessionResolver, java.util.concurrent.ForkJoinPool.commonPool(),
                Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC),
                Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(10));

        AgentTurnLifecycleService.Result result = service.execute(
                issued.issuedToken().encode(),
                new AgentTurnCommand.Ask(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("继续这个项目"),
                        "recommendation_handle_123", null, null));

        assertThat(result.turn()).isInstanceOf(PublicAgentTurn.Clarification.class);
        PublicAgentTurn.Clarification clarification =
                (PublicAgentTurn.Clarification) result.turn();
        assertThat(clarification.getClarification().getFields()).hasSize(1);
    }

    @Test void unknownOrCrossConversationHandleDoesNotLeakExistence() {
        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.findContext(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(store.completeWithSession(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(
                new TurnExecutionStore.SettlementResult(true, null));
        AgentTurnLifecycleService service = LifecycleTestFixture.service(
                store, com.portfolio.agent.turn.planning.ResolvedGoalSet.conversational("unused"));
        AgentTurnLifecycleService.Result result = service.execute(
                null, new AgentTurnCommand.Continue(
                        UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                        AgentTurnCommand.ContinueOperation.ROUTE_IN_CONTEXT,
                        "context_handle_123", null,
                        "继续说明", null, null, null));
        assertThat(result.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        PublicAgentTurn.CapabilityUnavailable turn =
                (PublicAgentTurn.CapabilityUnavailable) result.turn();
        assertThat(turn.getCode()).isEqualTo("DISCUSSION_CONTEXT_UNAVAILABLE");
        assertThat(turn.isRetryable()).isFalse();
    }

    private ActiveFixture activeFixture(
            com.portfolio.agent.turn.planning.GoalInterpretationResult interpretation) {
        return activeFixture(
                interpretation,
                LifecycleTestFixture.NOW.plus(Duration.ofMinutes(10)));
    }

    private ActiveFixture activeFixture(
            com.portfolio.agent.turn.planning.GoalInterpretationResult interpretation,
            java.time.Instant pointerExpiry) {
        Clock clock = Clock.fixed(LifecycleTestFixture.NOW, ZoneOffset.UTC);
        com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore sessions =
                new com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore();
        com.portfolio.agent.turn.continuation.ConversationSessionResolver sessionResolver =
                new com.portfolio.agent.turn.continuation.ConversationSessionResolver(
                        sessions, new byte[32], clock, Duration.ofMinutes(30));
        com.portfolio.agent.turn.continuation.ConversationSessionResolver.Resolution issued =
                sessionResolver.resolve(
                        null, UUID.randomUUID(),
                        com.portfolio.agent.turn.execution.TurnDeadline.after(
                                Duration.ofSeconds(5), clock));
        com.portfolio.agent.turn.continuation.ActiveDiscussionPointer pointer =
                new com.portfolio.agent.turn.continuation.ActiveDiscussionPointer(
                        "discussion_active_123", "project-a",
                        pointerExpiry);
        com.portfolio.agent.turn.continuation.ConversationSessionStore.Session pending =
                sessionResolver.pendingSession(issued);
        sessions.save(new com.portfolio.agent.turn.continuation.ConversationSessionStore.Session(
                pending.conversationId(), pending.tokenHash(),
                pending.createdAt(), pending.expiresAt(), pointer, 1));

        AgentStateStore store = mock(AgentStateStore.class);
        when(store.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TurnExecutionStore.ClaimResult.claimed());
        when(store.findContext(any(), any(), any(), any())).thenReturn(Optional.of(
                new com.portfolio.agent.turn.continuation.ProjectDiscussionContext(
                        "discussion_active_123", issued.conversationId(),
                        "public-1", LifecycleTestFixture.NOW.plus(Duration.ofMinutes(10)),
                        "project-a",
                        Set.of("project-a"), LifecycleTestFixture.NOW, null)));
        when(store.completeWithSession(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new TurnExecutionStore.SettlementResult(true, null));
        com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway knowledge =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway.class);
        com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent content =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent.class);
        com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge project =
                mock(com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge.class);
        when(project.getStableId()).thenReturn("project-a");
        when(project.getSlug()).thenReturn("project-a-slug");
        when(project.getTitle()).thenReturn("项目 A");
        when(content.getProjects()).thenReturn(List.of(project));
        when(content.getCases()).thenReturn(List.of());
        when(content.getContentVersion()).thenReturn("public-1");
        when(knowledge.getContent()).thenReturn(content);
        com.portfolio.agent.turn.planning.GoalResolver resolver =
                mock(com.portfolio.agent.turn.planning.GoalResolver.class);
        when(resolver.interpretTyped(any(), any(), any())).thenReturn(interpretation);
        com.portfolio.agent.turn.planning.SemanticPlanCompiler compiler =
                mock(com.portfolio.agent.turn.planning.SemanticPlanCompiler.class);
        AgentTurnLifecycleService service = new AgentTurnLifecycleService(
                knowledge, resolver,
                compiler,
                mock(com.portfolio.agent.turn.execution.SemanticTurnEngine.class),
                new com.portfolio.agent.turn.projection.PublicAgentTurnProjector(),
                new com.portfolio.agent.turn.continuation.ContextMutationPlanner(
                        () -> "context_handle_123"),
                store, new RequestFingerprintFactory(new byte[32]),
                sessionResolver, java.util.concurrent.ForkJoinPool.commonPool(),
                clock, Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(10));
        return new ActiveFixture(
                service, issued.issuedToken().encode(), resolver, store, compiler);
    }

    private record ActiveFixture(
            AgentTurnLifecycleService service,
            String token,
            com.portfolio.agent.turn.planning.GoalResolver resolver,
            AgentStateStore store,
            com.portfolio.agent.turn.planning.SemanticPlanCompiler compiler) { }
}
