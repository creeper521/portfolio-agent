package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationPort;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException;
import com.portfolio.agent.turn.planning.GoalProposalCodec;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GoalInterpretationAdapter implements GoalInterpretationPort {
    private final StructuredModelTransport transport;
    private final ObjectMapper mapper;
    private final GoalProposalCodec codec;
    private final String systemPrompt;
    private final int maxTokens;
    private final Duration timeout;
    private final ModelOutputDiagnostics outputDiagnostics;
    public GoalInterpretationAdapter(
            StructuredModelTransport transport, ObjectMapper mapper,
            GoalProposalCodec codec, String systemPrompt,
            int maxTokens, Duration timeout) {
        this(transport, mapper, codec, systemPrompt, maxTokens, timeout,
                ModelOutputDiagnostics.none());
    }
    public GoalInterpretationAdapter(
            StructuredModelTransport transport, ObjectMapper mapper,
            GoalProposalCodec codec, String systemPrompt,
            int maxTokens, Duration timeout,
            ModelOutputDiagnostics outputDiagnostics) {
        this.transport = transport; this.mapper = mapper; this.codec = codec;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens; this.timeout = timeout;
        this.outputDiagnostics = java.util.Objects.requireNonNull(
                outputDiagnostics, "outputDiagnostics");
    }
    @Override public GoalInterpretationResult interpret(
            GoalInterpretationInput input, TurnDeadline deadline,
            ResolvedModelExecution modelExecution) {
        if (!modelExecution.getSnapshot().supports(
                ModelCapability.TURN_INTERPRETATION)) {
            if (modelExecution.getSnapshot().getKind()
                    == com.portfolio.agent.infrastructure.model.ModelExecutionSnapshot.Kind.MODEL) {
                throw SelectedModelFailureException.unavailableBeforeAttempt();
            }
            throw new GoalInterpretationUnavailableException();
        }
        StructuredModelRequest request = new StructuredModelRequest(
                "GOAL_INTERPRETATION", systemPrompt, prompt(input), maxTokens, 0.0d,
                deadline.cappedAt(timeout));
        if (request.deadline().isExpired()) {
            throw SelectedModelFailureException
                    .temporarilyUnavailableBeforeAttempt();
        }
        modelExecution.markAttempted(
                ResolvedModelExecution.Stage.GOAL_INTERPRETATION);
        String json;
        try {
            json = transport.execute(
                    modelExecution.getRequiredBinding(), request).json();
        } catch (StructuredModelFailure failure) {
            throw SelectedModelFailureException.from(failure);
        }
        try {
            return codec.decode(json, input);
        } catch (IllegalArgumentException failure) {
            outputDiagnostics.rejected(
                    "GOAL_INTERPRETATION", ModelOutputDiagnostics.Layer.SCHEMA);
            throw SelectedModelFailureException.invalidResponse(failure);
        }
    }
    private String prompt(GoalInterpretationInput input) {
        try {
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("currentInput", input.getUserText());
            projection.put("recentConversation", input.getRecentMessages());
            projection.put("recentSemanticState", input.getRecentSemanticState());
            projection.put("interpretationMode", input.getInterpretationMode());
            projection.put("discussionState", input.getDiscussionState());
            projection.put("allowedGoalKinds", input.getAllowedGoalKinds());
            projection.put("allowedRoutes", input.getAllowedRoutes());
            projection.put("allowedRecommendationConstraints",
                    input.getAllowedRecommendationConstraints());
            projection.put("defaultSubject", input.getDefaultSubject() == null
                    ? null : subject(input.getDefaultSubject()));
            projection.put("audienceProfile", input.getAudienceProfile());
            projection.put("publicSubjects", input.getPublicSubjects().stream().map(subject -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("kind", subject.getKind()); value.put("reference", subject.getReference());
                value.put("reviewedLabel", subject.getLabel());
                value.put("reviewedAliases", subject.getReviewedAliases()); return value;
            }).toList());
            projection.put("lockedSubject", input.getLockedSubject() == null
                    ? null : subject(input.getLockedSubject()));
            projection.put("routeCandidates", input.getRouteCandidates().stream()
                    .map(candidate -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("candidateKey", candidate.getCandidateKey());
                        value.put("kind", candidate.getKind());
                        value.put("reference", candidate.getReference());
                        value.put("reviewedLabel", candidate.getLabel());
                        value.put("reviewedAliases", candidate.getReviewedAliases());
                        return value;
                    }).toList());
            projection.put("schema", "semantic-route-proposal-v1");
            return mapper.writeValueAsString(projection);
        } catch (Exception failure) {
            throw new GoalInterpretationUnavailableException(failure);
        }
    }

    private Map<String, Object> subject(
            GoalInterpretationInput.PublicSubjectDescriptor subject) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", subject.getKind());
        value.put("reference", subject.getReference());
        value.put("reviewedLabel", subject.getLabel());
        value.put("reviewedAliases", subject.getReviewedAliases());
        return value;
    }
}
