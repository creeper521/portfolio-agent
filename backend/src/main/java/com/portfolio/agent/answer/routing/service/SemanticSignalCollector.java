package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects ordered current-question goal candidates; it never inspects conversation messages. */
public final class SemanticSignalCollector {

    private static final Pattern EXPLICIT_TASK_COUNT = Pattern.compile(
            "(?:^|\\D)([1-9][0-9]*)\\s*(?:\u4e2a|\u9879)?\\s*(?:\u72ec\u7acb)?\u4efb\u52a1");

    public SemanticSignals collect(SemanticTurnInput input, ResolvedRoutingContext context) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(context, "context");
        String question = normalize(input.getQuestion());
        List<SemanticSignals.GoalCandidate> goals = new ArrayList<>();

        boolean comparison = containsAny(question, "\u6bd4\u8f83", "\u5bf9\u6bd4", "compare");
        boolean introduction = containsAny(question, "\u4ecb\u7ecd", "\u6982\u8ff0", "\u5ba1\u8ba1", "\u804c\u8d23", "\u8bf4\u660e\u9879\u76ee");
        boolean recommendation = containsAny(question, "\u63a8\u8350", "recommend");
        boolean synthesis = containsAny(question, "\u7efc\u5408", "\u603b\u7ed3", "\u7ed3\u8bba");
        boolean generalExplanation = containsAny(question, "\u89e3\u91ca", "\u539f\u7406", "\u662f\u4ec0\u4e48") && !introduction;

        if (introduction) {
            addFactGoals(goals, context.getSubjects(), question);
        }
        boolean comparisonMissingSubjects = false;
        if (comparison) {
            if (context.getSubjects().size() < 2) {
                comparisonMissingSubjects = true;
            } else {
                goals.add(new SemanticSignals.GoalCandidate(
                        SemanticSignals.Intent.PORTFOLIO_COMPARE,
                        List.copyOf(context.getSubjects().subList(0, Math.min(3, context.getSubjects().size())))));
            }
        }
        if (recommendation) {
            goals.add(new SemanticSignals.GoalCandidate(
                    SemanticSignals.Intent.PORTFOLIO_RECOMMEND, context.getSubjects()));
        }
        if (generalExplanation) {
            goals.add(new SemanticSignals.GoalCandidate(
                    SemanticSignals.Intent.GENERAL_EXPLANATION, List.of()));
        }
        if (generalExplanation && !context.getSubjects().isEmpty()) {
            goals.removeIf(goal -> goal.getIntent() == SemanticSignals.Intent.GENERAL_EXPLANATION);
            addFactGoals(goals, context.getSubjects(), question);
        }
        if (synthesis && countNonSynthesis(goals) >= 2) {
            goals.add(new SemanticSignals.GoalCandidate(SemanticSignals.Intent.SYNTHESIS, List.of()));
        }

        List<PlanExclusion> exclusions = containsAny(
                question, "\u4e0d\u8981\u63a8\u8350", "\u4e0d\u5305\u542b\u63a8\u8350", "exclude recommendation")
                ? List.of(PlanExclusion.planOutput(RequestedOutput.RECOMMENDATION))
                : List.of();
        if (!exclusions.isEmpty()) {
            goals.removeIf(goal -> goal.getIntent() == SemanticSignals.Intent.PORTFOLIO_RECOMMEND);
        }

        boolean unresolvedPortfolioIntent = introduction && context.getSubjects().isEmpty();
        boolean safeIndependentGoal = goals.stream().anyMatch(goal ->
                goal.getIntent() != SemanticSignals.Intent.PORTFOLIO_COMPARE
                        && goal.getIntent() != SemanticSignals.Intent.PORTFOLIO_FACT
                        || goal.getIntent() == SemanticSignals.Intent.PORTFOLIO_FACT
                        && !goal.getSubjects().isEmpty());
        SemanticSignals.ClarificationNeed clarificationNeed;
        if (unresolvedPortfolioIntent) {
            clarificationNeed = SemanticSignals.ClarificationNeed.CRITICAL;
        } else if (comparisonMissingSubjects) {
            clarificationNeed = safeIndependentGoal
                    ? SemanticSignals.ClarificationNeed.LOCAL
                    : SemanticSignals.ClarificationNeed.CRITICAL;
        } else {
            clarificationNeed = SemanticSignals.ClarificationNeed.NONE;
        }
        if (comparisonMissingSubjects) {
            goals.removeIf(goal -> goal.getIntent() == SemanticSignals.Intent.PORTFOLIO_COMPARE);
        }
        if (goals.isEmpty() && !context.getSubjects().isEmpty()) {
            addFactGoals(goals, context.getSubjects(), question);
        } else if (goals.isEmpty() && !introduction && !comparison && !recommendation && !synthesis
                && explicitTaskCount(question) <= 6) {
            goals.add(new SemanticSignals.GoalCandidate(
                    SemanticSignals.Intent.GENERAL_EXPLANATION, List.of()));
        }

        int requestedTaskCount = Math.max(explicitTaskCount(question), goals.size());
        return new SemanticSignals(
                goals,
                exclusions,
                requestedTaskCount,
                clarificationNeed,
                containsAny(question, "\u5148", "\u518d", "\u7136\u540e", "\u6700\u540e"),
                false,
                false);
    }

    private void addFactGoals(
            List<SemanticSignals.GoalCandidate> goals,
            List<SubjectReference> subjects,
            String question) {
        if (subjects.isEmpty()) {
            goals.add(new SemanticSignals.GoalCandidate(SemanticSignals.Intent.PORTFOLIO_FACT, List.of()));
            return;
        }
        boolean oneGoalPerSubject = containsAny(question, "\u5206\u522b", "\u6bcf\u4e2a", "\u5404\u4e2a", "\u8fd9\u4e9b")
                || countOccurrences(question, "\u4ecb\u7ecd") > 1;
        if (oneGoalPerSubject) {
            for (SubjectReference subject : subjects) {
                goals.add(new SemanticSignals.GoalCandidate(
                        SemanticSignals.Intent.PORTFOLIO_FACT, List.of(subject)));
            }
            return;
        }
        goals.add(new SemanticSignals.GoalCandidate(
                SemanticSignals.Intent.PORTFOLIO_FACT, List.of(subjects.get(0))));
    }

    private static int countNonSynthesis(List<SemanticSignals.GoalCandidate> goals) {
        int count = 0;
        for (SemanticSignals.GoalCandidate goal : goals) {
            if (goal.getIntent() != SemanticSignals.Intent.SYNTHESIS) {
                count++;
            }
        }
        return count;
    }

    private static int countOccurrences(String value, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }

    private static int explicitTaskCount(String question) {
        Matcher matcher = EXPLICIT_TASK_COUNT.matcher(question);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static boolean containsAny(String question, String... markers) {
        for (String marker : markers) {
            if (question.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT);
    }
}

final class SemanticSignals {

    enum Intent {
        PORTFOLIO_FACT,
        PORTFOLIO_COMPARE,
        PORTFOLIO_RECOMMEND,
        GENERAL_EXPLANATION,
        SYNTHESIS
    }

    enum ClarificationNeed {
        NONE,
        LOCAL,
        CRITICAL
    }

    private final List<GoalCandidate> goals;
    private final List<com.portfolio.agent.answer.routing.domain.PlanExclusion> exclusions;
    private final int requestedTaskCount;
    private final ClarificationNeed clarificationNeed;
    private final boolean userDeclaredOrder;
    private final boolean orderAdjusted;
    private final boolean nodeCapabilityBoundary;

    SemanticSignals(
            List<GoalCandidate> goals,
            List<com.portfolio.agent.answer.routing.domain.PlanExclusion> exclusions,
            int requestedTaskCount,
            ClarificationNeed clarificationNeed,
            boolean userDeclaredOrder,
            boolean orderAdjusted,
            boolean nodeCapabilityBoundary) {
        this.goals = List.copyOf(Objects.requireNonNull(goals, "goals"));
        this.exclusions = List.copyOf(Objects.requireNonNull(exclusions, "exclusions"));
        this.requestedTaskCount = requestedTaskCount;
        this.clarificationNeed = Objects.requireNonNull(clarificationNeed, "clarificationNeed");
        this.userDeclaredOrder = userDeclaredOrder;
        this.orderAdjusted = orderAdjusted;
        this.nodeCapabilityBoundary = nodeCapabilityBoundary;
    }

    List<GoalCandidate> getGoals() { return goals; }

    /** Compatibility projection for diagnostics; compilation consumes ordered goals instead. */
    List<Intent> getIntents() {
        Set<Intent> intents = new LinkedHashSet<>();
        for (GoalCandidate goal : goals) {
            intents.add(goal.getIntent());
        }
        return List.copyOf(intents);
    }

    List<SubjectReference> getSubjects() {
        List<SubjectReference> subjects = new ArrayList<>();
        for (GoalCandidate goal : goals) {
            subjects.addAll(goal.getSubjects());
        }
        return List.copyOf(new LinkedHashSet<>(subjects));
    }

    List<com.portfolio.agent.answer.routing.domain.PlanExclusion> getExclusions() { return exclusions; }
    int getRequestedTaskCount() { return requestedTaskCount; }
    ClarificationNeed getClarificationNeed() { return clarificationNeed; }
    boolean isUserDeclaredOrder() { return userDeclaredOrder; }
    boolean isOrderAdjusted() { return orderAdjusted; }
    boolean hasNodeCapabilityBoundary() { return nodeCapabilityBoundary; }
    boolean hasUnresolvedPortfolioGoal() {
        for (GoalCandidate goal : goals) {
            if (goal.getIntent() == Intent.PORTFOLIO_FACT && goal.getSubjects().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static final class GoalCandidate {
        private final Intent intent;
        private final List<SubjectReference> subjects;

        GoalCandidate(Intent intent, List<SubjectReference> subjects) {
            this.intent = Objects.requireNonNull(intent, "intent");
            this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        }

        Intent getIntent() { return intent; }
        List<SubjectReference> getSubjects() { return subjects; }
    }
}
