package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerDecision;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.domain.QuestionKind;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.AnswerContextRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.gateway.AnswerDecisionPublisher;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioAgentRuntimeTest {

    @Test
    void readsOneRuntimeSnapshotAndPublishesOnlyAnAnonymousDecision() {
        Fixture fixture = fixture();

        AnswerResult result = fixture.runtime().answer(fixture.request());

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        verify(fixture.knowledgeGateway(), times(1)).getContent();
        ArgumentCaptor<AnswerDecision> decisionCaptor = ArgumentCaptor.forClass(AnswerDecision.class);
        verify(fixture.publisher()).publish(decisionCaptor.capture());
        AnswerDecision decision = decisionCaptor.getValue();
        assertThat(decision.getQuestionKind()).isEqualTo(QuestionKind.FREE_TEXT);
        assertThat(decision.getContentVersion()).isEqualTo("2026-07-21");
        assertThat(decision.getProjectSlug()).isEqualTo("sql-audit");
        assertThat(decision.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(AnswerDecision.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("question", "requestId", "turnId");
    }

    @Test
    void publisherFailureNeverChangesTheAnswer() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("publisher unavailable"))
                .when(fixture.publisher()).publish(org.mockito.ArgumentMatchers.any());

        AnswerResult result = fixture.runtime().answer(fixture.request());

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.BOUNDARY);
        assertThat(result.getSections()).hasSize(1);
    }

    private Fixture fixture() {
        PortfolioKnowledgeGateway knowledgeGateway = mock(PortfolioKnowledgeGateway.class);
        QuestionResolver questionResolver = mock(QuestionResolver.class);
        AnswerContextFactory contextFactory = mock(AnswerContextFactory.class);
        AnswerEngine answerEngine = mock(AnswerEngine.class);
        VerificationPolicy verificationPolicy = mock(VerificationPolicy.class);
        AnswerDecisionPublisher publisher = mock(AnswerDecisionPublisher.class);

        AnswerKnowledge project = project();
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21", "sha256:runtime", List.of(project));
        AnswerRequest request = new AnswerRequest(
                "turn-1",
                null,
                "an unsupported visitor question",
                new AnswerContextRequest(
                        "sql-audit", AudienceRole.GUEST, List.of(), AnswerRequestSource.AGENT_PAGE)
        );
        QuestionResolution resolution = new QuestionResolution(
                AnswerResolution.BOUNDARY, project, null);

        when(knowledgeGateway.getContent()).thenReturn(content);
        when(questionResolver.resolve(content, request)).thenReturn(resolution);
        when(contextFactory.create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(resolution),
                org.mockito.ArgumentMatchers.eq(request)))
                .thenAnswer(invocation -> new ResolvedAnswerContext(
                        invocation.getArgument(0), project, null, List.of()));

        PortfolioAgentRuntime runtime = new PortfolioAgentRuntime(
                knowledgeGateway,
                questionResolver,
                contextFactory,
                answerEngine,
                verificationPolicy,
                publisher
        );
        return new Fixture(runtime, request, knowledgeGateway, publisher);
    }

    private AnswerKnowledge project() {
        return new AnswerKnowledge(
                "sql-audit",
                "SQL audit tool",
                "Public summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                "Handoff",
                "DELIVERED",
                List.of(new AnswerQuestion(
                        "sql-audit-overview", "Describe the project", List.of(), "Overview")),
                List.of()
        );
    }

    private static final class Fixture {
        private final PortfolioAgentRuntime runtime;
        private final AnswerRequest request;
        private final PortfolioKnowledgeGateway knowledgeGateway;
        private final AnswerDecisionPublisher publisher;

        private Fixture(
                PortfolioAgentRuntime runtime,
                AnswerRequest request,
                PortfolioKnowledgeGateway knowledgeGateway,
                AnswerDecisionPublisher publisher
        ) {
            this.runtime = runtime;
            this.request = request;
            this.knowledgeGateway = knowledgeGateway;
            this.publisher = publisher;
        }

        private PortfolioAgentRuntime runtime() { return runtime; }
        private AnswerRequest request() { return request; }
        private PortfolioKnowledgeGateway knowledgeGateway() { return knowledgeGateway; }
        private AnswerDecisionPublisher publisher() { return publisher; }
    }
}
