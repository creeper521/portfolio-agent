package com.portfolio.agent.turn.projection;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.general.GeneralPresentation;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.capability.synthesis.CrossDomainPresentation;
import com.portfolio.agent.turn.execution.GoalCoverage;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.portfolio.agent.turn.continuation.ContinuationReference;

/** Projects only ordered fulfillment Goals. Supporting Tasks never become public content. */
public final class PublicAgentTurnProjector {
    public PublicAgentTurn.Answer project(
            UUID requestId, SemanticTurnPlan plan, SemanticTurnOutcome outcome) {
        return project(requestId, plan, outcome, Map.of());
    }

    public PublicAgentTurn.Answer project(
            UUID requestId, SemanticTurnPlan plan, SemanticTurnOutcome outcome,
            Map<String, String> continuationsByGoal) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(outcome, "outcome");
        Map<String, TaskOutcome> taskOutcomes = indexTasks(outcome);
        Map<String, GoalCoverage.Coverage> coverage = indexCoverage(outcome);
        LinkedHashMap<String, PublicSourceCatalog.Source> sources = new LinkedHashMap<>();
        List<AnswerGoalResult> goals = new ArrayList<>();
        for (UserGoal goal : plan.getUserGoals()) {
            TaskOutcome task = required(taskOutcomes, goal.getFulfillmentTaskId(), "fulfillment task");
            GoalCoverage.Coverage goalCoverage = required(coverage, goal.getGoalId(), "goal coverage");
            goals.add(projectGoal(
                    goal, task, goalCoverage, sources,
                    continuationsByGoal.get(goal.getGoalId())));
        }
        PublicAnswer.Resolution resolution = resolution(goals);
        LinkedHashSet<PublicSupport.Kind> composition = new LinkedHashSet<>();
        goals.forEach(goal -> collectComposition(goal.getPresentation(), composition));
        PublicAnswer answer = new PublicAnswer(
                resolution, plan.getContentReleaseId(), goals,
                new PublicSourceCatalog(List.copyOf(sources.values())),
                List.copyOf(composition), List.of(), null);
        return new PublicAgentTurn.Answer(requestId, answer);
    }

    private AnswerGoalResult projectGoal(
            UserGoal goal, TaskOutcome outcome, GoalCoverage.Coverage coverage,
            LinkedHashMap<String, PublicSourceCatalog.Source> sources,
            String continuationHandle) {
        AnswerGoalResult.Coverage publicCoverage = AnswerGoalResult.Coverage.valueOf(coverage.name());
        if (coverage == GoalCoverage.Coverage.NONE) {
            if (outcome.getProducedArtifact().isPresent()) {
                throw new IllegalArgumentException("NONE goal cannot reference a produced fulfillment task");
            }
            return new AnswerGoalResult(
                    goal.getGoalId(), goal.getLabel(), publicCoverage, null,
                    List.of(notice(outcome)));
        }
        TaskArtifact artifact = outcome.getProducedArtifact().orElseThrow(() ->
                new IllegalArgumentException("produced goal requires fulfillment artifact"));
        PublicPresentation presentation = presentation(
                goal, artifact, sources, continuationHandle);
        List<GoalNotice> notices = coverage == GoalCoverage.Coverage.PARTIAL
                ? gapNotices(artifact) : List.of();
        if (coverage == GoalCoverage.Coverage.FULL && !notices.isEmpty()) {
            throw new IllegalArgumentException("FULL goal cannot contain gap notices");
        }
        return new AnswerGoalResult(
                goal.getGoalId(), goal.getLabel(), publicCoverage,
                presentation, notices);
    }

    private PublicPresentation presentation(
            UserGoal goal, TaskArtifact artifact,
            LinkedHashMap<String, PublicSourceCatalog.Source> sources,
            String continuationHandle) {
        if (artifact.getSemanticResult() instanceof PortfolioSemanticResult.Recommendation recommendation) {
            return recommendation(
                    goal, recommendation, sources, continuationHandle);
        }
        if (artifact.getPresentation() instanceof PortfolioPresentation value) {
            List<PublicSection> sections = new ArrayList<>();
            for (int index = 0; index < value.getSections().size(); index++) {
                PortfolioPresentation.Section section = value.getSections().get(index);
                addSources(section.getSources(), sources);
                sections.add(section(goal, index, section.getSectionType(), section.getTitle(),
                        section.getContent(), PublicSupport.Kind.VERIFIED_PUBLIC_EVIDENCE,
                        keys(section.getSources())));
            }
            return new PublicPresentation.Sectioned(sections);
        }
        if (artifact.getPresentation() instanceof GeneralPresentation value) {
            List<PublicSection> sections = new ArrayList<>();
            for (int index = 0; index < value.getSections().size(); index++) {
                GeneralPresentation.Section section = value.getSections().get(index);
                sections.add(new PublicSection(
                        sectionId(goal, index), AnswerSectionType.GENERAL_PRINCIPLE,
                        section.title(), section.content(),
                        new PublicSupport(PublicSupport.Kind.GENERAL_KNOWLEDGE, List.of())));
            }
            return new PublicPresentation.Sectioned(sections);
        }
        if (artifact.getPresentation() instanceof CrossDomainPresentation value) {
            List<PublicSection> sections = new ArrayList<>();
            for (int index = 0; index < value.getSections().size(); index++) {
                CrossDomainPresentation.Section section = value.getSections().get(index);
                addSources(section.sources(), sources);
                PublicSupport.Kind supportKind = switch (index) {
                    case 0 -> PublicSupport.Kind.GENERAL_KNOWLEDGE;
                    case 1 -> PublicSupport.Kind.VERIFIED_PUBLIC_EVIDENCE;
                    default -> PublicSupport.Kind.DERIVED;
                };
                AnswerSectionType sectionKind = switch (index) {
                    case 0 -> AnswerSectionType.GENERAL_PRINCIPLE;
                    case 1 -> AnswerSectionType.PORTFOLIO_EXAMPLE;
                    default -> AnswerSectionType.RELATION;
                };
                sections.add(new PublicSection(
                        sectionId(goal, index), sectionKind, section.title(), section.content(),
                        new PublicSupport(supportKind, keys(section.sources()))));
            }
            return new PublicPresentation.Sectioned(sections);
        }
        throw new IllegalArgumentException("fulfillment artifact has no supported public presentation");
    }

    private PublicPresentation.Recommendation recommendation(
            UserGoal goal, PortfolioSemanticResult.Recommendation result,
            LinkedHashMap<String, PublicSourceCatalog.Source> sources,
            String continuationHandle) {
        List<PublicPresentation.Recommendation.Item> items = new ArrayList<>();
        for (int index = 0; index < result.getItems().size(); index++) {
            PortfolioSemanticResult.Recommendation.RecommendationItem semanticItem =
                    result.getItems().get(index);
            String subjectId = semanticItem.subjectId();
            List<com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit> units =
                    result.getUnits().stream().filter(value -> value.getSubjectId().equals(subjectId)).toList();
            if (units.isEmpty()) throw new IllegalArgumentException("recommendation item lacks support");
            units.forEach(value -> addSource(value.getSourceReference(), sources));
            List<String> sourceKeys = units.stream()
                    .map(value -> value.getSourceReference().getReferenceKey()).distinct().toList();
            PublicSourceReferenceValue first = units.getFirst().getSourceReference();
            String resultItemId =
                    "item-" + goal.getGoalId() + "-" + (index + 1);
            SuggestedAction discussionAction = continuationHandle == null
                    ? null : new SuggestedAction(
                    "discuss-" + resultItemId,
                    "与我讨论", null,
                    ContinuationReference.enterResult(
                            continuationHandle, resultItemId));
            items.add(new PublicPresentation.Recommendation.Item(
                    resultItemId,
                    units.getFirst().getSubjectTitle(),
                    units.stream().map(value -> value.getClaim().getStatement())
                            .collect(java.util.stream.Collectors.joining("\n")),
                    first.getSubjectRoute(), semanticItem.reasonCodes().stream()
                            .map(this::recommendationReason).toList(),
                    new PublicSupport(
                            PublicSupport.Kind.VERIFIED_PUBLIC_EVIDENCE,
                            sourceKeys),
                    discussionAction));
        }
        List<String> incompleteReasons = result.getCoverage() == PortfolioSemanticResult.Coverage.PARTIAL
                ? (result.getOmissions().isEmpty() ? List.of("公开结果数量不足") : result.getOmissions())
                : List.of();
        return new PublicPresentation.Recommendation(
                result.getRequestedSize(), items, result.getUnsatisfiedConstraints(),
                incompleteReasons, List.of());
    }

    private String recommendationReason(
            PortfolioSemanticResult.Recommendation.RecommendationReasonCode code) {
        return switch (code) {
            case CAREER_TRACK_MATCH -> "职业方向符合筛选条件";
            case CAPABILITY_MATCH -> "能力标签符合筛选条件";
            case VERIFIED_IMPLEMENTATION -> "具备公开可验证的实现材料";
            case VERIFIED_VERIFICATION -> "具备公开可验证的验证材料";
            case VERIFIED_OUTCOME -> "具备公开可验证的结果材料";
            case VERIFIED_PUBLIC_EVIDENCE -> "具备公开可验证材料";
        };
    }

    private List<GoalNotice> gapNotices(TaskArtifact artifact) {
        if (artifact.getSemanticResult() instanceof PortfolioSemanticResult value
                && !value.getOmissions().isEmpty()) {
            return value.getOmissions().stream().map(omission ->
                    new GoalNotice("COVERAGE_INCOMPLETE", "当前公开材料未完整覆盖：" + omission)).toList();
        }
        return List.of(new GoalNotice("COVERAGE_INCOMPLETE", "当前结果仅覆盖了部分目标。"));
    }

    private GoalNotice notice(TaskOutcome outcome) {
        TaskTerminalReason reason = outcome.getTerminal() instanceof TaskOutcome.ReasonTerminal value
                ? value.getReason() : TaskTerminalReason.NO_SUPPORTED_RESULT;
        return switch (reason) {
            case NO_SUPPORTED_RESULT -> new GoalNotice(
                    "NO_SUPPORTED_EVIDENCE", "当前公开材料不足以支持这个结论。");
            case INPUT_REJECTED -> new GoalNotice("OUT_OF_SCOPE", "当前请求超出可安全处理的范围。");
            case CAPABILITY_UNAVAILABLE, EXECUTION_FAILED -> new GoalNotice(
                    "CAPABILITY_UNAVAILABLE", "当前暂时无法完成这个目标。");
            case DEPENDENCY_UNAVAILABLE, NOT_SCHEDULED -> new GoalNotice(
                    "DEPENDENCY_UNAVAILABLE", "完成这个目标所需的前置结果不可用。");
            case TURN_CANCELLED -> new GoalNotice("TURN_CANCELLED", "这个目标已取消。");
            case TURN_DEADLINE_EXCEEDED -> new GoalNotice("TIMED_OUT", "这个目标未能在时限内完成。");
        };
    }

    private PublicAnswer.Resolution resolution(List<AnswerGoalResult> goals) {
        if (goals.stream().allMatch(value -> value.getCoverage() == AnswerGoalResult.Coverage.FULL)) {
            return PublicAnswer.Resolution.COMPLETE;
        }
        if (goals.stream().allMatch(value -> value.getCoverage() == AnswerGoalResult.Coverage.NONE)) {
            return PublicAnswer.Resolution.NO_RESULT;
        }
        return PublicAnswer.Resolution.PARTIAL;
    }

    private void collectComposition(
            PublicPresentation presentation, LinkedHashSet<PublicSupport.Kind> composition) {
        if (presentation instanceof PublicPresentation.Sectioned sectioned) {
            sectioned.getSections().forEach(value -> composition.add(value.getSupport().getKind()));
        }
        if (presentation instanceof PublicPresentation.Recommendation recommendation) {
            recommendation.getItems().forEach(value -> composition.add(value.getSupport().getKind()));
            recommendation.getSupportingSections().forEach(
                    value -> composition.add(value.getSupport().getKind()));
        }
    }

    private PublicSection section(
            UserGoal goal, int index, AnswerSectionType type,
            String title, String content, PublicSupport.Kind support, List<String> keys) {
        return new PublicSection(
                sectionId(goal, index), type,
                title, content, new PublicSupport(support, keys));
    }
    private String sectionId(UserGoal goal, int index) {
        return "section-" + goal.getGoalId() + "-" + (index + 1);
    }

    private void addSources(
            List<PublicSourceReferenceValue> values,
            LinkedHashMap<String, PublicSourceCatalog.Source> sources) {
        values.forEach(value -> sources.putIfAbsent(value.getReferenceKey(),
                new PublicSourceCatalog.Source(
                        value.getReferenceKey(), value.getReferenceKey(), value.getLabel(),
                        "PUBLIC_EVIDENCE", value.getEvidenceRoute())));
    }
    private void addSource(
            PublicSourceReferenceValue value,
            LinkedHashMap<String, PublicSourceCatalog.Source> sources) {
        sources.putIfAbsent(value.getReferenceKey(), new PublicSourceCatalog.Source(
                value.getReferenceKey(), value.getReferenceKey(), value.getLabel(),
                "PUBLIC_EVIDENCE", value.getEvidenceRoute()));
    }
    private List<String> keys(List<PublicSourceReferenceValue> values) {
        return values.stream().map(PublicSourceReferenceValue::getReferenceKey).distinct().toList();
    }

    private Map<String, TaskOutcome> indexTasks(SemanticTurnOutcome outcome) {
        LinkedHashMap<String, TaskOutcome> values = new LinkedHashMap<>();
        outcome.getTaskOutcomes().forEach(value -> {
            if (values.putIfAbsent(value.getTaskId(), value) != null) {
                throw new IllegalArgumentException("duplicate task outcome");
            }
        });
        return values;
    }
    private Map<String, GoalCoverage.Coverage> indexCoverage(SemanticTurnOutcome outcome) {
        LinkedHashMap<String, GoalCoverage.Coverage> values = new LinkedHashMap<>();
        outcome.getGoalCoverage().forEach(value -> {
            if (values.putIfAbsent(value.getGoalId(), value.getCoverage()) != null) {
                throw new IllegalArgumentException("duplicate goal coverage");
            }
        });
        return values;
    }
    private <K, V> V required(Map<K, V> values, K key, String name) {
        V value = values.get(key);
        if (value == null) throw new IllegalArgumentException(name + " is missing");
        return value;
    }
}
