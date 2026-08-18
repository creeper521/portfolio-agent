package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.execution.CancellationSignal;
import com.portfolio.agent.turn.execution.GoalCoverage;
import com.portfolio.agent.turn.execution.SectionedTaskPresentation;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalResolutionContext;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.PlanCompilationResult;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Slice-1 runtime; removed when Slice 5 introduces AgentTurnLifecycleService. */
public final class MigrationAgentTurnRuntime {
    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final GoalResolver goalResolver;
    private final SemanticPlanCompiler planCompiler;
    private final SemanticTurnEngine engine;

    public MigrationAgentTurnRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            GoalResolver goalResolver,
            SemanticPlanCompiler planCompiler,
            SemanticTurnEngine engine) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
        this.goalResolver = Objects.requireNonNull(goalResolver, "goalResolver");
        this.planCompiler = Objects.requireNonNull(planCompiler, "planCompiler");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public ConversationAnswerResult answer(AgentTurnCommand command) {
        Objects.requireNonNull(command, "command");
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        ResolvedGoalSet resolved = goalResolver.resolve(command, resolutionContext(content));
        return switch (resolved.getKind()) {
            case CONVERSATIONAL -> message(command, content, ConversationIntent.CONVERSATION,
                    ConversationAnswerScope.CONVERSATION, AnswerResolution.ANSWERED,
                    resolved.getMessage().orElseThrow());
            case BOUNDARY -> message(command, content, ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                    ConversationAnswerScope.GLOBAL, AnswerResolution.BOUNDARY,
                    resolved.getMessage().orElseThrow());
            case CAPABILITY_UNAVAILABLE -> message(command, content, ConversationIntent.GENERAL_KNOWLEDGE,
                    ConversationAnswerScope.GENERAL, AnswerResolution.CAPABILITY_UNAVAILABLE,
                    resolved.getMessage().orElseThrow(), presetFailureCode(command, content));
            case INVALID_INPUT -> message(command, content, ConversationIntent.PORTFOLIO_GROUNDED,
                    ConversationAnswerScope.PORTFOLIO, AnswerResolution.INVALID_INPUT,
                    resolved.getMessage().orElseThrow(), "STRUCTURED_SUBJECT_INVALID");
            case CLARIFICATION -> message(command, content, ConversationIntent.PORTFOLIO_GROUNDED,
                    ConversationAnswerScope.PORTFOLIO, AnswerResolution.NEEDS_CLARIFICATION,
                    resolved.getClarification().orElseThrow().getPrompt());
            case GOALS -> execute(command, content, planCompiler.compile(
                    resolved.getGoalProposal().orElseThrow(), content.getContentVersion(),
                    resolutionContext(content)));
        };
    }

    private ConversationAnswerResult execute(
            AgentTurnCommand command,
            RuntimeAnswerContent content,
            PlanCompilationResult compilation) {
        if (compilation.getKind() == PlanCompilationResult.Kind.CLARIFICATION_REQUIRED) {
            return message(command, content, ConversationIntent.PORTFOLIO_GROUNDED,
                    ConversationAnswerScope.PORTFOLIO, AnswerResolution.NEEDS_CLARIFICATION,
                    "需要明确公开主体后才能继续。");
        }
        if (compilation.getKind() != PlanCompilationResult.Kind.COMPILED) {
            return message(command, content, ConversationIntent.PORTFOLIO_GROUNDED,
                    ConversationAnswerScope.PORTFOLIO, AnswerResolution.REJECTED,
                    "当前请求无法形成安全的执行计划。");
        }
        SemanticTurnPlan plan = compilation.getPlan().orElseThrow().getPlan();
        SemanticTurnOutcome outcome = engine.execute(
                compilation.getPlan().orElseThrow(),
                TurnDeadline.after(java.time.Duration.ofSeconds(10), java.time.Clock.systemUTC()),
                new CancellationSignal(), List.of(), isPreset(command));
        List<ConversationAnswerBlock> blocks = blocks(plan, outcome);
        boolean answered = !blocks.isEmpty();
        boolean partial = answered && outcome.getGoalCoverage().stream().anyMatch(value ->
                value.getCoverage() != GoalCoverage.Coverage.FULL);
        ConversationIntent intent = intent(plan);
        ConversationAnswerScope scope = scope(plan);
        ConversationAnswerResult result = new ConversationAnswerResult(
                command.getRequestId().toString(), content.getContentVersion(), intent, scope,
                answered ? (partial ? AnswerResolution.PARTIALLY_ANSWERED : AnswerResolution.ANSWERED)
                        : AnswerResolution.NOT_SUPPORTED,
                answered ? "回答" : "暂无可公开结果", blocks, List.of(), false,
                com.portfolio.agent.answer.domain.GenerationMode.DETERMINISTIC,
                isPreset(command) ? com.portfolio.agent.answer.domain.AnswerSource.PRESET
                        : com.portfolio.agent.answer.domain.AnswerSource.RETRIEVAL,
                null);
        if (command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.Preset preset) {
            return result.withContractIdentity(preset.getPresetId(), preset.getPresetRevision());
        }
        return result;
    }

    private List<ConversationAnswerBlock> blocks(
            SemanticTurnPlan plan, SemanticTurnOutcome outcome) {
        java.util.Map<String, TaskOutcome> outcomes = new java.util.LinkedHashMap<>();
        outcome.getTaskOutcomes().forEach(value -> outcomes.put(value.getTaskId(), value));
        List<ConversationAnswerBlock> blocks = new ArrayList<>();
        for (com.portfolio.agent.turn.planning.UserGoal goal : plan.getUserGoals()) {
            TaskOutcome taskOutcome = outcomes.get(goal.getFulfillmentTaskId());
            if (taskOutcome == null || taskOutcome.getProducedArtifact().isEmpty()) continue;
            com.portfolio.agent.turn.execution.TaskPresentation presentation =
                    taskOutcome.getProducedArtifact().orElseThrow().getPresentation();
            ConversationSourceScope sourceScope = sourceScope(
                    plan.findTask(goal.getFulfillmentTaskId()).orElseThrow().getSourceDomain());
            if (presentation instanceof SectionedTaskPresentation sectioned) {
                for (SectionedTaskPresentation.Section block : sectioned.getSections()) {
                    blocks.add(new ConversationAnswerBlock(
                            sourceScope, block.getSectionType(), block.getTitle(),
                            block.getContent(), List.of(), List.of(), block.getSourceReferences()));
                }
                continue;
            }
            if (presentation instanceof com.portfolio.agent.answer.domain.PortfolioAnswerPlan portfolioPlan) {
                for (com.portfolio.agent.answer.domain.PortfolioAnswerSection section : portfolioPlan.getSections()) {
                    blocks.add(new ConversationAnswerBlock(
                            sourceScope, section.getSectionType(), section.getTitle(), section.getContent(),
                            section.getClaimIds(), section.getEvidenceIds(), section.getSourceReferences()));
                }
                continue;
            }
            if (presentation instanceof
                    com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation
                    portfolioPresentation) {
                for (com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation.Section
                        section : portfolioPresentation.getSections()) {
                    blocks.add(new ConversationAnswerBlock(
                            sourceScope, section.getSectionType(), section.getTitle(), section.getContent(),
                            List.of(), List.of(), section.getSources()));
                }
                continue;
            }
            if (presentation instanceof
                    com.portfolio.agent.turn.capability.general.GeneralPresentation generalPresentation) {
                for (com.portfolio.agent.turn.capability.general.GeneralPresentation.Section
                        section : generalPresentation.getSections()) {
                    blocks.add(new ConversationAnswerBlock(
                            sourceScope, section.sectionType(), section.title(), section.content(),
                            List.of(), List.of(), List.of()));
                }
                continue;
            }
            if (presentation instanceof
                    com.portfolio.agent.turn.capability.synthesis.CrossDomainPresentation synthesisPresentation) {
                for (com.portfolio.agent.turn.capability.synthesis.CrossDomainPresentation.Section
                        section : synthesisPresentation.getSections()) {
                    blocks.add(new ConversationAnswerBlock(
                            sourceScope, section.sectionType(), section.title(), section.content(),
                            List.of(), List.of(), section.sources()));
                }
            }
        }
        return List.copyOf(blocks);
    }

    private ConversationSourceScope sourceScope(
            com.portfolio.agent.turn.planning.SemanticTask.SourceDomain domain) {
        return switch (domain) {
            case PORTFOLIO -> ConversationSourceScope.PORTFOLIO;
            case GENERAL -> ConversationSourceScope.GENERAL;
            case SYNTHESIS -> ConversationSourceScope.PORTFOLIO;
        };
    }

    private ConversationIntent intent(SemanticTurnPlan plan) {
        boolean portfolio = plan.getTasks().stream().anyMatch(task ->
                task.getSourceDomain() == com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.PORTFOLIO);
        boolean general = plan.getTasks().stream().anyMatch(task ->
                task.getSourceDomain() == com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL);
        if (portfolio && general) return ConversationIntent.HYBRID;
        return portfolio ? ConversationIntent.PORTFOLIO_GROUNDED : ConversationIntent.GENERAL_KNOWLEDGE;
    }

    private ConversationAnswerScope scope(SemanticTurnPlan plan) {
        boolean portfolio = plan.getTasks().stream().anyMatch(task ->
                task.getSourceDomain() == com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.PORTFOLIO);
        boolean general = plan.getTasks().stream().anyMatch(task ->
                task.getSourceDomain() == com.portfolio.agent.turn.planning.SemanticTask.SourceDomain.GENERAL);
        if (portfolio && general) return ConversationAnswerScope.HYBRID;
        return portfolio ? ConversationAnswerScope.PORTFOLIO : ConversationAnswerScope.GENERAL;
    }

    private ConversationAnswerResult message(
            AgentTurnCommand command,
            RuntimeAnswerContent content,
            ConversationIntent intent,
            ConversationAnswerScope scope,
            AnswerResolution resolution,
            String text) {
        return message(command, content, intent, scope, resolution, text, null);
    }

    private ConversationAnswerResult message(
            AgentTurnCommand command,
            RuntimeAnswerContent content,
            ConversationIntent intent,
            ConversationAnswerScope scope,
            AnswerResolution resolution,
            String text,
            String noticeCode) {
        List<ConversationAnswerBlock> blocks = resolution == AnswerResolution.ANSWERED
                || resolution == AnswerResolution.INVALID_INPUT
                ? List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.GENERAL, text, List.of(), List.of()))
                : List.of();
        return new ConversationAnswerResult(
                command.getRequestId().toString(), content.getContentVersion(), intent, scope,
                resolution, text, blocks, List.of(), false,
                com.portfolio.agent.answer.domain.GenerationMode.DETERMINISTIC,
                isPreset(command) ? com.portfolio.agent.answer.domain.AnswerSource.PRESET : null,
                noticeCode);
    }

    private String presetFailureCode(AgentTurnCommand command, RuntimeAnswerContent content) {
        if (!(command instanceof AgentTurnCommand.Ask ask)
                || !(ask.getInput() instanceof AgentTurnCommand.Preset preset)) return null;
        for (com.portfolio.agent.answer.domain.AnswerKnowledge knowledge : content.getProjects()) {
            for (com.portfolio.agent.answer.domain.AnswerQuestion question : knowledge.getQuestions()) {
                if (preset.getPresetId().equals(question.getId())) {
                    return preset.getPresetRevision().equals(question.getContractVersion())
                            ? null : "PRESET_CONTRACT_STALE";
                }
            }
        }
        for (com.portfolio.agent.answer.domain.AnswerKnowledge knowledge : content.getCases()) {
            for (com.portfolio.agent.answer.domain.AnswerQuestion question : knowledge.getQuestions()) {
                if (preset.getPresetId().equals(question.getId())) {
                    return preset.getPresetRevision().equals(question.getContractVersion())
                            ? null : "PRESET_CONTRACT_STALE";
                }
            }
        }
        return "PRESET_CONTRACT_UNAVAILABLE";
    }

    private boolean isPreset(AgentTurnCommand command) {
        return command instanceof AgentTurnCommand.Ask ask
                && ask.getInput() instanceof AgentTurnCommand.Preset;
    }

    private GoalResolutionContext resolutionContext(RuntimeAnswerContent content) {
        List<GoalInterpretationInput.PublicSubjectDescriptor> subjects = new ArrayList<>();
        content.getProjects().forEach(value -> subjects.add(
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT,
                        value.getStableId(), value.getTitle(),
                        Set.of(value.getStableId(), value.getSlug(), value.getTitle()))));
        content.getCases().forEach(value -> subjects.add(
                new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.CASE,
                        value.getStableId(), value.getTitle(),
                        Set.of(value.getStableId(), value.getSlug(), value.getTitle()))));
        return new GoalResolutionContext(List.copyOf(subjects), Set.of(GoalKind.values()));
    }
}
