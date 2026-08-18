package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentStatePayloadCodecTest {
    @Test void encryptedPublicSnapshotRoundTripsAndAadRejectsCrossRequestReplay() {
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                JsonMapper.builder().addModule(new ParameterNamesModule())
                        .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build(),
                "state-key-1", new byte[32]);
        UUID requestId = UUID.randomUUID();
        PublicAgentTurn turn = new PublicAgentTurn.Conversational(requestId, "你好", List.of());
        AgentStatePayloadCodec.Envelope envelope = codec.encode(
                requestId, "conversation-1",
                new AgentStatePayloadCodec.SettlementPayload(turn, List.of(), List.of()));
        assertThat(codec.decode(requestId, "conversation-1", envelope).publicTurn())
                .isInstanceOf(PublicAgentTurn.Conversational.class);
        assertThatThrownBy(() -> codec.decode(UUID.randomUUID(), "conversation-1", envelope))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void typedContextAndChallengeRoundTripWithoutRenderedOrRawEvidenceState() {
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                JsonMapper.builder().addModule(new ParameterNamesModule())
                        .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build(),
                "state-key-1", new byte[32]);
        UUID requestId = UUID.randomUUID();
        var context = new com.portfolio.agent.turn.continuation.ContinuationContext.PortfolioFact(
                "context_handle_123", "conversation-1", "public-1",
                java.time.Instant.parse("2026-08-18T00:05:00Z"),
                java.util.Set.of("project-a"), java.util.Set.of("SOLUTION"));
        var challenge = new com.portfolio.agent.turn.continuation.ClarificationChallenge(
                "clarification_123", "请补充", List.of(
                new com.portfolio.agent.turn.continuation.ClarificationChallenge.TextField(
                        "field_detail", "补充", true, 100)), List.of());
        var record = new com.portfolio.agent.turn.continuation.ClarificationStore.Record(
                "conversation-1", new byte[32], "public-1", challenge,
                java.util.Map.of(), java.util.Map.of("field_detail",
                new com.portfolio.agent.turn.continuation.ClarificationStore.TextBinding("goal:detail", 100)));
        var contextEnvelope = codec.encodeContext(requestId, "conversation-1", context);
        var challengeEnvelope = codec.encodeChallenge(requestId, "conversation-1", record);
        assertThat(codec.decodeContext(
                requestId, "conversation-1", "context_handle_123", contextEnvelope).getKind())
                .isEqualTo(com.portfolio.agent.turn.continuation.ContinuationContext.Kind.PORTFOLIO_FACT);
        assertThat(codec.decodeChallenge(
                requestId, "conversation-1", "clarification_123", challengeEnvelope)
                .challenge().getPrompt()).isEqualTo("请补充");
    }
}
