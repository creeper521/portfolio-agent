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
    private static final String SYSTEM_PROMPT = """
            Return exactly one JSON object with no Markdown and no unknown fields.
            Root variants are:
            {"kind":"CONVERSATIONAL","message":"..."}
            {"kind":"CLARIFICATION","clarification":{"field":"SUBJECT|GOAL|OUTPUT","prompt":"...","inputAnchor":{"text":"exact substring","start":0}}}
            or {"kind":"GOALS","goals":[goal]}.
            Every goal has exactly goalKey, goalKind, inputAnchor, subjectCandidates,
            requestedOutputs, knowledgeRequirement, parameters. Anchors must copy an exact
            substring of currentInput and use its zero-based character start.
            For a stable concept explanation use:
            {"goalKey":"general-goal","goalKind":"GENERAL_EXPLANATION",
             "inputAnchor":{"text":"exact request phrase","start":0},
             "subjectCandidates":[],"requestedOutputs":["EXPLANATION"],
             "knowledgeRequirement":"STABLE_GENERAL_EXPLANATION",
             "parameters":{"kind":"GENERAL_EXPLANATION","topicAnchor":{"text":"exact topic substring","start":0},"depth":"STANDARD"}}.
            Public subjects may only use supplied kind/reference with basis EXPLICIT_NAME and
            an exact anchor. Never output tasks, dependencies, tools, providers, or invented IDs.
            """;
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
