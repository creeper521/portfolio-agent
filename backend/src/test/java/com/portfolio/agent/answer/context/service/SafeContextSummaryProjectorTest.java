package com.portfolio.agent.answer.context.service;

import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextSummary;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeContextSummaryProjectorTest {
    @Test
    void summaryContainsOnlySafeLabelsAndContinuationAffordance() {
        String sourceTaskId = "internal-task-123";
        RecentSemanticTaskContext context = new RecentSemanticTaskContext(
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_RECOMMEND,
                List.of(SubjectReference.project("private-project-id", "content-v9")),
                Set.of("overview"), Set.of("impact"), "content-v9", sourceTaskId);
        ConversationContextMutation mutation = new ConversationContextMutation(
                ContextHandle.issue(), ConversationContextValue.recentSemanticTask(context), null,
                sourceTaskId, 128, null, null);
        ConversationContextEntry entry = new ConversationContextEntry(
                ConversationId.random(), mutation.getContextHandle(), mutation.getValue(), null,
                sourceTaskId, 128, Instant.parse("2026-08-12T04:00:00Z"),
                Instant.parse("2026-08-12T04:00:00Z"), Instant.parse("2026-08-13T04:00:00Z"),
                Instant.parse("2026-08-19T04:00:00Z"));

        ConversationContextSummary summary = new SafeContextSummaryProjector().project(entry);

        assertEquals("RECOMMENDATION", summary.getRecentTaskType());
        assertEquals(List.of(), summary.getSubjectLabels());
        assertEquals(List.of("OVERVIEW"), summary.getFacetLabels());
        assertEquals(List.of("IMPACT"), summary.getComparisonDimensionLabels());
        assertTrue(summary.isCanRefine());
        String rendered = summary.toString();
        assertFalse(rendered.contains(sourceTaskId));
        assertFalse(rendered.contains("private-project-id"));
        assertFalse(rendered.contains("content-v9"));
        assertFalse(rendered.contains("question"));
        assertFalse(rendered.contains("answer"));
    }

    @Test
    void recommendationSummaryProjectsPreferencesButNotParentHandleOrScopeIds() {
        RecommendationContext context = new RecommendationContext(
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("secret-id", "v1")), "v1"),
                "profile-v3", Set.of("BASELINE"), Set.of("NO_PERSONAL_DATA"),
                Set.of("LOW_RISK"), Set.of("EXCLUDED"), 3, ContextHandle.issue());
        ConversationContextMutation mutation = new ConversationContextMutation(
                ContextHandle.issue(), ConversationContextValue.recommendation(context),
                context.getParentContextHandle(), "recommendation-task", 128, null, null);
        ConversationContextEntry entry = new ConversationContextEntry(
                ConversationId.random(), mutation.getContextHandle(), mutation.getValue(),
                mutation.getParentContextHandle(), "recommendation-task", 128,
                Instant.parse("2026-08-12T04:00:00Z"), Instant.parse("2026-08-12T04:00:00Z"),
                Instant.parse("2026-08-13T04:00:00Z"), Instant.parse("2026-08-19T04:00:00Z"));

        ConversationContextSummary summary = new SafeContextSummaryProjector().project(entry);

        assertEquals("REFINE", summary.getRecentTaskType());
        assertEquals(List.of("BASELINE", "EXCLUDED", "NO_PERSONAL_DATA"),
                summary.getFacetLabels());
        assertEquals(List.of("LOW_RISK"), summary.getPreferenceLabels());
        assertTrue(summary.isCanRefine());
        assertFalse(summary.toString().contains("secret-id"));
        assertFalse(summary.toString().contains("profile-v3"));
    }
}
