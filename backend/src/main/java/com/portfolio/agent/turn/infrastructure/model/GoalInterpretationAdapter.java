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

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GoalInterpretationAdapter implements GoalInterpretationPort {
    private static final String SYSTEM_PROMPT = "Interpret the visitor input into the closed user-goal JSON schema supplied in the data. Emit JSON only. You may describe user goals or a clarification need, but you have no authority to design execution plans, select tools, choose providers, or invent subjects.";
    private final StructuredModelTransport transport;
    private final ObjectMapper mapper;
    private final GoalProposalCodec codec;
    private final int maxTokens;
    private final Duration timeout;
    private final Clock clock;
    public GoalInterpretationAdapter(
            StructuredModelTransport transport, ObjectMapper mapper,
            GoalProposalCodec codec, int maxTokens, Duration timeout, Clock clock) {
        this.transport = transport; this.mapper = mapper; this.codec = codec;
        this.maxTokens = maxTokens; this.timeout = timeout; this.clock = clock;
    }
    @Override public GoalInterpretationResult interpret(GoalInterpretationInput input) {
        try {
            return codec.decode(transport.execute(new StructuredModelRequest(
                    "GOAL_INTERPRETATION", SYSTEM_PROMPT, prompt(input), maxTokens, 0.0d,
                    TurnDeadline.after(timeout, clock))).json(), input);
        } catch (StructuredModelFailure | IllegalArgumentException failure) {
            throw new GoalInterpretationUnavailableException(failure);
        }
    }
    private String prompt(GoalInterpretationInput input) {
        try {
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("currentInput", input.getUserText());
            projection.put("recentConversation", input.getRecentMessages());
            projection.put("allowedGoalKinds", input.getAllowedGoalKinds());
            projection.put("publicSubjects", input.getPublicSubjects().stream().map(subject -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("kind", subject.getKind()); value.put("reference", subject.getReference());
                value.put("reviewedLabel", subject.getLabel());
                value.put("reviewedAliases", subject.getReviewedAliases()); return value;
            }).toList());
            projection.put("schema", "user-goal-proposal-v1");
            return mapper.writeValueAsString(projection);
        } catch (Exception failure) {
            throw new GoalInterpretationUnavailableException(failure);
        }
    }
}
