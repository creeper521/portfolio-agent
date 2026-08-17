package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Preserves the first occurrence of each task meaning before plan compilation assigns task ids. */
public final class SemanticGoalDeduplicator {

    private SemanticGoalDeduplicator() { }

    public static List<SemanticSignals.GoalCandidate> distinctGoals(
            List<SemanticSignals.GoalCandidate> goals) {
        LinkedHashMap<GoalKey, SemanticSignals.GoalCandidate> distinct = new LinkedHashMap<>();
        for (SemanticSignals.GoalCandidate goal : goals) {
            distinct.putIfAbsent(new GoalKey(
                    goal.getIntent(),
                    subjectKeys(goal.getSubjects()),
                    goal.getTopics().stream().map(SemanticGoalDeduplicator::normalized).sorted().toList(),
                    goal.getPortfolioFacets().stream().map(Enum::name).sorted().toList()), goal);
        }
        return List.copyOf(distinct.values());
    }

    private static List<String> subjectKeys(List<SubjectReference> subjects) {
        return subjects.stream()
                .map(subject -> subject.getSubjectType() + ":" + subject.getSubjectId()
                        + ":" + subject.getContentVersion())
                .sorted()
                .toList();
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record GoalKey(
            SemanticSignals.Intent intent,
            List<String> subjects,
            List<String> topics,
            List<String> facets) { }
}
