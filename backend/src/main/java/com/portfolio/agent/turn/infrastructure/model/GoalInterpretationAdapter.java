package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.StructuredModelTransport;
import com.portfolio.agent.turn.execution.TurnDeadline;
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
    public GoalInterpretationAdapter(
            StructuredModelTransport transport, ObjectMapper mapper,
            GoalProposalCodec codec, String systemPrompt,
            int maxTokens, Duration timeout) {
        this.transport = transport; this.mapper = mapper; this.codec = codec;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt is required");
        }
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens; this.timeout = timeout;
    }
    @Override public GoalInterpretationResult interpret(
            GoalInterpretationInput input, TurnDeadline deadline) {
        try {
            return codec.decode(transport.execute(new StructuredModelRequest(
                    "GOAL_INTERPRETATION", systemPrompt, prompt(input), maxTokens, 0.0d,
                    deadline.cappedAt(timeout))).json(), input);
        } catch (StructuredModelFailure | IllegalArgumentException failure) {
            throw new GoalInterpretationUnavailableException(failure);
        }
    }
    private String prompt(GoalInterpretationInput input) {
        try {
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("currentInput", input.getUserText());
            projection.put("recentConversation", input.getRecentMessages());
            projection.put("interpretationMode", input.getInterpretationMode());
            projection.put("discussionState", input.getDiscussionState());
            projection.put("allowedGoalKinds", input.getAllowedGoalKinds());
            projection.put("allowedRoutes", input.getAllowedRoutes());
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
