package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.PortfolioFacet;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;
import com.portfolio.agent.answer.routing.domain.TurnProposal;
import com.portfolio.agent.answer.routing.gateway.TurnInterpretationPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Rebinds a closed proposal to the supplied public catalog before any plan can be validated or executed. */
public final class ProposalCompiler {

    private final SemanticRoutingPolicy routingPolicy;
    private final SemanticPlanValidator planValidator;
    private final PageReferenceMarkerCatalog pageReferenceMarkerCatalog;
    private final ReferenceMatchPolicy referenceMatchPolicy;

    public ProposalCompiler(SemanticRoutingPolicy routingPolicy) {
        this.routingPolicy = Objects.requireNonNull(routingPolicy, "routingPolicy");
        this.planValidator = new SemanticPlanValidator(new PlanFingerprintService());
        this.pageReferenceMarkerCatalog = loadPageReferenceMarkerCatalog();
        this.referenceMatchPolicy = new ReferenceMatchPolicy();
    }

    public ProposalCompilationResult compile(
            TurnProposal proposal,
            TurnInterpretationPort.TurnInterpretationInput input) {
        if (proposal == null || input == null || proposal.getKind() != TurnProposal.Kind.PROPOSE_EXECUTION) {
            return ProposalCompilationResult.rejected(ProposalCompilationResult.ReasonCode.PROPOSAL_INVALID);
        }
        for (TurnProposal.TaskProposal task : proposal.getTasks()) {
            if (!input.getAllowedTaskTypes().contains(task.getTaskType())) {
                return ProposalCompilationResult.rejected(ProposalCompilationResult.ReasonCode.TASK_TYPE_UNSUPPORTED);
            }
        }
        try {
            java.util.Map<String, String> taskIds = taskIds(proposal);
            List<SemanticTask> tasks = new ArrayList<>();
            for (TurnProposal.TaskProposal task : proposal.getTasks()) {
                tasks.add(compileTask(task, input, tasks.size() + 1, taskIds));
            }
            List<TaskDependency> dependencies = compileDependencies(proposal, tasks);
            tasks = assignFulfillmentRoles(tasks, dependencies);
            SemanticTurnPlan plan = new SemanticTurnPlan(
                    freshPlanId(), contentVersion(input.getPublicSubjects()),
                    SemanticTurnPlan.PlanSource.MODEL_ASSISTED, tasks, dependencies, List.of(),
                    requestedOutputs(tasks), SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
            PlanValidationResult validation = planValidator.validate(
                    plan, SemanticTurnContractPolicy.CURRENT_CONTRACT);
            if (!validation.isValid()) {
                return ProposalCompilationResult.rejected(ProposalCompilationResult.ReasonCode.PROPOSAL_INVALID);
            }
            return ProposalCompilationResult.compiled(validation.getValidatedPlan().orElseThrow().getPlan());
        } catch (SubjectNotPublicException exception) {
            return ProposalCompilationResult.rejected(ProposalCompilationResult.ReasonCode.SUBJECT_NOT_PUBLIC);
        } catch (IllegalArgumentException exception) {
            return ProposalCompilationResult.rejected(ProposalCompilationResult.ReasonCode.PROPOSAL_INVALID);
        }
    }

    private SemanticTask compileTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal,
            java.util.Map<String, String> taskIds) {
        return switch (task.getTaskType()) {
            case PORTFOLIO_FACT -> factTask(task, input, ordinal);
            case PORTFOLIO_COMPARE -> comparisonTask(task, input, ordinal);
            case PORTFOLIO_RECOMMEND -> recommendationTask(task, input, ordinal);
            case PORTFOLIO_REFINE_RECOMMENDATION -> refinementTask(task, input, ordinal);
            case GENERAL_EXPLANATION -> generalExplanationTask(task, input, ordinal);
            case GENERAL_COMPARISON -> generalComparisonTask(task, input, ordinal);
            case SYNTHESIS -> synthesisTask(task, ordinal, taskIds);
            default -> throw new IllegalArgumentException("task type is not yet supported by the compiler");
        };
    }

    private SemanticTask synthesisTask(
            TurnProposal.TaskProposal task, int ordinal, java.util.Map<String, String> taskIds) {
        if (!task.getSubjectCandidates().isEmpty() || !task.getTopicAnchors().isEmpty()
                || task.getSourceTaskKeys().size() < 2 || task.getSourceTaskKeys().size() > 6) {
            throw new IllegalArgumentException("synthesis requires two to six source task keys only");
        }
        List<String> sourceTaskIds = new ArrayList<>();
        for (String sourceTaskKey : task.getSourceTaskKeys()) {
            String sourceTaskId = taskIds.get(sourceTaskKey);
            if (sourceTaskId == null || sourceTaskId.equals(String.format("task-%02d", ordinal))) {
                throw new IllegalArgumentException("synthesis source task must reference another task");
            }
            sourceTaskIds.add(sourceTaskId);
        }
        SemanticTaskParameters.Synthesis parameters = new SemanticTaskParameters.Synthesis(
                sourceTaskIds, "形成综合结论", Set.of());
        return SemanticTask.create(String.format("task-%02d", ordinal), SemanticTaskType.SYNTHESIS,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS,
                "形成综合结论", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), List.of());
    }

    private List<TaskDependency> compileDependencies(
            TurnProposal proposal, List<SemanticTask> tasks) {
        java.util.Map<String, String> taskIds = taskIds(proposal);
        List<TaskDependency> dependencies = new ArrayList<>();
        for (TurnProposal.ProposalDependency dependency : proposal.getDependencies()) {
            dependencies.add(new TaskDependency(taskIds.get(dependency.getFromClientTaskKey()),
                    taskIds.get(dependency.getToClientTaskKey()), dependency.getDependencyType(),
                    com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin.USER_EXPLICIT));
        }
        for (int index = 0; index < proposal.getTasks().size(); index++) {
            TurnProposal.TaskProposal task = proposal.getTasks().get(index);
            if (task.getTaskType() == SemanticTaskType.SYNTHESIS) {
                for (String sourceKey : task.getSourceTaskKeys()) {
                    dependencies.add(new TaskDependency(taskIds.get(sourceKey), tasks.get(index).getTaskId(),
                            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType.REQUIRES_SUCCESS,
                            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.DependencyOrigin.COMPILER_INFERRED));
                }
            }
        }
        return List.copyOf(dependencies);
    }

    private List<SemanticTask> assignFulfillmentRoles(List<SemanticTask> tasks, List<TaskDependency> dependencies) {
        Set<String> synthesisSources = new LinkedHashSet<>();
        for (SemanticTask task : tasks) {
            if (task.getTaskType() == SemanticTaskType.SYNTHESIS) {
                synthesisSources.addAll(((SemanticTaskParameters.Synthesis) task.getParameters()).getSourceTaskIds());
            }
        }
        if (synthesisSources.isEmpty()) {
            return tasks;
        }
        List<SemanticTask> assigned = new ArrayList<>();
        for (SemanticTask task : tasks) {
            TaskFulfillmentRole role = synthesisSources.contains(task.getTaskId())
                    ? TaskFulfillmentRole.SUPPORTING : TaskFulfillmentRole.PRIMARY;
            assigned.add(SemanticTask.create(task.getTaskId(), task.getTaskType(), task.getSourceDomain(),
                    task.getGoalLabel(), task.getParameters(), task.getRequestedOutputs(), task.getConfidence(),
                    task.getSubjectReferences(), role));
        }
        return List.copyOf(assigned);
    }

    private java.util.Map<String, String> taskIds(TurnProposal proposal) {
        java.util.Map<String, String> taskIds = new java.util.LinkedHashMap<>();
        for (int index = 0; index < proposal.getTasks().size(); index++) {
            taskIds.put(proposal.getTasks().get(index).getClientTaskKey(), String.format("task-%02d", index + 1));
        }
        return taskIds;
    }

    private SemanticTask factTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal) {
        if (task.getSubjectCandidates().size() != 1) {
            throw new IllegalArgumentException("portfolio fact requires one subject candidate");
        }
        task.getInputAnchor().resolveIn(input.getCurrentInput());
        SubjectReference subject = resolveSubject(task.getSubjectCandidates().getFirst(), input);
        SemanticTaskParameters.PortfolioFact parameters = new SemanticTaskParameters.PortfolioFact(
                subject, task.getFacets().isEmpty() ? Set.of(PortfolioFacet.OVERVIEW.name()) : task.getFacets(), "GUEST");
        return SemanticTask.create(
                String.format("task-%02d", ordinal), SemanticTaskType.PORTFOLIO_FACT,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "介绍公开项目", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), List.of(subject));
    }

    private SemanticTask comparisonTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal) {
        if (task.getSubjectCandidates().size() < 2 || task.getSubjectCandidates().size() > 3) {
            throw new IllegalArgumentException("portfolio comparison requires two or three subject candidates");
        }
        task.getInputAnchor().resolveIn(input.getCurrentInput());
        List<SubjectReference> subjects = new ArrayList<>();
        for (TurnProposal.SubjectCandidate candidate : task.getSubjectCandidates()) {
            subjects.add(resolveSubject(candidate, input));
        }
        SemanticTaskParameters.PortfolioCompare parameters = new SemanticTaskParameters.PortfolioCompare(
                subjects, task.getDimensions().isEmpty()
                        ? Set.of(ComparisonDimension.ARCHITECTURE.name()) : task.getDimensions(), "GUEST");
        return SemanticTask.create(
                String.format("task-%02d", ordinal), SemanticTaskType.PORTFOLIO_COMPARE,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "比较公开项目", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), subjects);
    }

    private SemanticTask generalExplanationTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal) {
        if (!task.getSubjectCandidates().isEmpty()) {
            throw new IllegalArgumentException("general explanation cannot contain subject candidates");
        }
        String topic = task.getInputAnchor().resolveIn(input.getCurrentInput()).getText();
        SemanticTaskParameters.GeneralExplanation parameters = new SemanticTaskParameters.GeneralExplanation(
                topic, explanationDepth(task.getResponseMode()), "GUEST");
        return SemanticTask.create(
                String.format("task-%02d", ordinal), SemanticTaskType.GENERAL_EXPLANATION,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                "解释通用概念", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), List.of());
    }

    private SemanticTask recommendationTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal) {
        if (!task.getSubjectCandidates().isEmpty()) {
            throw new IllegalArgumentException("recommendation candidates are derived by the server");
        }
        task.getInputAnchor().resolveIn(input.getCurrentInput());
        List<SubjectReference> projects = new ArrayList<>();
        for (SubjectReference subject : input.getPublicSubjects()) {
            if (subject.getSubjectType() == SubjectType.PROJECT) {
                projects.add(new SubjectReference(subject.getSubjectType(), subject.getSubjectId(),
                        SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE, subject.getContentVersion()));
            }
        }
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("recommendation requires public projects");
        }
        SemanticTaskParameters.PortfolioRecommend parameters = new SemanticTaskParameters.PortfolioRecommend(
                projects, task.getCareerTrack().orElse("BACKEND_ENGINEERING"), task.getCapabilityFilters(),
                "岗位匹配", task.getRequestedSize().orElse(2), "GUEST");
        return SemanticTask.create(
                String.format("task-%02d", ordinal), SemanticTaskType.PORTFOLIO_RECOMMEND,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "给出岗位推荐", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), projects);
    }

    private SemanticTask refinementTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal) {
        if (task.getSubjectCandidates().size() != 1) {
            throw new IllegalArgumentException("refinement requires exactly one recent result candidate");
        }
        task.getInputAnchor().resolveIn(input.getCurrentInput());
        TurnProposal.SubjectCandidate candidate = task.getSubjectCandidates().getFirst();
        if (candidate.getBasis() != TurnProposal.SubjectBasis.RECENT_RESULT
                || candidate.getSubjectType() != SubjectType.RESULT) {
            throw new IllegalArgumentException("refinement requires a recent result candidate");
        }
        SubjectReference result = resolveRecentResult(candidate, input);
        SemanticTaskParameters.PortfolioRefinement parameters = new SemanticTaskParameters.PortfolioRefinement(
                result, task.getConstraints(), Set.of());
        return SemanticTask.create(
                String.format("task-%02d", ordinal), SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "调整岗位推荐", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), List.of(result));
    }

    private SemanticTask generalComparisonTask(
            TurnProposal.TaskProposal task,
            TurnInterpretationPort.TurnInterpretationInput input,
            int ordinal) {
        if (!task.getSubjectCandidates().isEmpty() || task.getTopicAnchors().size() < 2
                || task.getTopicAnchors().size() > 3) {
            throw new IllegalArgumentException("general comparison requires two or three topic anchors only");
        }
        List<String> topics = new ArrayList<>();
        for (com.portfolio.agent.answer.routing.domain.TextAnchor anchor : task.getTopicAnchors()) {
            topics.add(anchor.resolveIn(input.getCurrentInput()).getText());
        }
        SemanticTaskParameters.GeneralComparison parameters = new SemanticTaskParameters.GeneralComparison(
                topics, task.getDimensions().isEmpty()
                        ? Set.of(ComparisonDimension.ARCHITECTURE.name()) : task.getDimensions(),
                explanationDepth(task.getResponseMode()), "GUEST");
        return SemanticTask.create(
                String.format("task-%02d", ordinal), SemanticTaskType.GENERAL_COMPARISON,
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                "比较通用主题", parameters, task.getRequestedOutputs(), TaskConfidence.highRule(), List.of());
    }

    private SubjectReference resolveSubject(
            TurnProposal.SubjectCandidate candidate,
            TurnInterpretationPort.TurnInterpretationInput input) {
        if (candidate.getBasis() == TurnProposal.SubjectBasis.CONFIRMED_SUBJECT) {
            for (SubjectReference confirmedSubject : input.getConfirmedSubjects()) {
                if (sameSubject(candidate, confirmedSubject)) {
                    return validated(confirmedSubject);
                }
            }
            throw new SubjectNotPublicException();
        }
        if (candidate.getBasis() == TurnProposal.SubjectBasis.PENDING_INTERACTION) {
            for (SubjectReference pendingSubject : input.getPendingInteractionSubjects()) {
                if (sameSubject(candidate, pendingSubject)) {
                    return validated(pendingSubject);
                }
            }
            throw new SubjectNotPublicException();
        }
        if (candidate.getBasis() == TurnProposal.SubjectBasis.PAGE_HINT) {
            SubjectReference hint = input.getPageHint().orElseThrow(SubjectNotPublicException::new);
            if (!sameSubject(candidate, hint) || !candidate.getEvidenceAnchor().isPresent()
                    || !pageReferenceMarkerCatalog.supports(candidate.getEvidenceAnchor().orElseThrow(),
                    input.getCurrentInput(), candidate.getSubjectType())) {
                throw new SubjectNotPublicException();
            }
            return validated(hint);
        }
        if (candidate.getBasis() != TurnProposal.SubjectBasis.EXPLICIT_INPUT) {
            throw new IllegalArgumentException("subject basis is not yet supported by the compiler");
        }
        candidate.getEvidenceAnchor().orElseThrow(() ->
                new IllegalArgumentException("explicit subject requires an evidence anchor"))
                .resolveIn(input.getCurrentInput());
        for (SubjectReference publicSubject : input.getPublicSubjects()) {
            if (sameSubject(candidate, publicSubject)) {
                boolean aliasMatches = input.describe(publicSubject).orElseThrow()
                        .getReviewedAliases().stream().anyMatch(alias -> referenceMatchPolicy.matches(
                                candidate.getEvidenceAnchor().orElseThrow(), input.getCurrentInput(), alias));
                if (!aliasMatches) {
                    throw new SubjectNotPublicException();
                }
                return validated(publicSubject);
            }
        }
        throw new SubjectNotPublicException();
    }

    private boolean sameSubject(TurnProposal.SubjectCandidate candidate, SubjectReference subject) {
        return subject.getSubjectType() == candidate.getSubjectType()
                && subject.getSubjectId().equals(candidate.getSubjectId());
    }

    private SubjectReference validated(SubjectReference publicSubject) {
        return new SubjectReference(publicSubject.getSubjectType(), publicSubject.getSubjectId(),
                SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE, publicSubject.getContentVersion());
    }

    private PageReferenceMarkerCatalog loadPageReferenceMarkerCatalog() {
        java.io.InputStream input = ProposalCompiler.class.getResourceAsStream(
                "/routing/page-reference-markers.v1.json");
        return PageReferenceMarkerCatalog.load(input);
    }

    private SubjectReference resolveRecentResult(
            TurnProposal.SubjectCandidate candidate,
            TurnInterpretationPort.TurnInterpretationInput input) {
        for (SubjectReference publicSubject : input.getPublicSubjects()) {
            if (publicSubject.getSubjectType() == SubjectType.RESULT
                    && publicSubject.getSubjectId().equals(candidate.getSubjectId())) {
                return new SubjectReference(SubjectType.RESULT, publicSubject.getSubjectId(),
                        SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE, null);
            }
        }
        throw new SubjectNotPublicException();
    }

    private String contentVersion(List<SubjectReference> subjects) {
        for (SubjectReference subject : subjects) {
            if (subject.getContentVersion() != null) {
                return subject.getContentVersion();
            }
        }
        throw new IllegalArgumentException("public subject catalog must have a content version");
    }

    private Set<com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput> requestedOutputs(
            List<SemanticTask> tasks) {
        Set<com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput> outputs = new LinkedHashSet<>();
        for (SemanticTask task : tasks) {
            outputs.addAll(task.getRequestedOutputs());
        }
        return Set.copyOf(outputs);
    }

    private String freshPlanId() {
        return "plan-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String explanationDepth(TurnProposal.ResponseMode responseMode) {
        return switch (responseMode) {
            case CONCISE -> "BRIEF";
            case STANDARD -> "STANDARD";
            case DETAILED -> "DETAILED";
        };
    }

    private static final class SubjectNotPublicException extends RuntimeException {
    }
}
