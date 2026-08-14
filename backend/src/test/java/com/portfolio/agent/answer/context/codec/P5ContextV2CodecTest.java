package com.portfolio.agent.answer.context.codec;

import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.OrderedSubjectSelection;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.SubjectOrderKind;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class P5ContextV2CodecTest {
    @Test
    void registryReadsAndWritesRecentV2() {
        SubjectReference first = new SubjectReference(SemanticRoutingTypes.SubjectType.PROJECT, "project-a",
                SemanticRoutingTypes.SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1");
        SubjectReference second = new SubjectReference(SemanticRoutingTypes.SubjectType.PROJECT, "project-b",
                SemanticRoutingTypes.SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1");
        RecentSemanticTaskContext context = new RecentSemanticTaskContext(
                SemanticRoutingTypes.SemanticTaskType.GENERAL_COMPARISON, List.of(first, second),
                Set.of("CONCURRENCY"), Set.of("LATENCY"), "public-v1", "task-1",
                new OrderedSubjectSelection(SubjectOrderKind.USER_DECLARED_ORDER,
                        List.of(new OrderedSubjectSelection.Item(1, first), new OrderedSubjectSelection.Item(2, second))));
        ConversationContextCodecRegistry registry = ConversationContextCodecRegistry.defaults();
        ConversationContextCodecRegistry.EncodedContext written = registry.encode(
                ConversationContextType.RECENT_SEMANTIC_TASK, context);
        assertThat(written.getSchemaVersion()).isEqualTo("p5-recent-v2");
        RecentSemanticTaskContext decoded = (RecentSemanticTaskContext) registry.decode(
                new ConversationContextCodecRegistry.EncodedContext(
                        ConversationContextType.RECENT_SEMANTIC_TASK, "p5-recent-v2",
                        new RecentSemanticTaskContextV2Codec().encode(context)));
        assertThat(decoded.getOrderedSelection().getOrderKind()).isEqualTo(SubjectOrderKind.USER_DECLARED_ORDER);
        assertThat(decoded.getOrderedSelection().getItems()).extracting(value -> value.getSubject().getSubjectId())
                .containsExactly("project-a", "project-b");
    }
}
