package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentStatePayloadCodecTest {
    @Test void productionNonNullMapperStillPersistsNullableBlockedGoalFields() {
        com.fasterxml.jackson.databind.ObjectMapper productionMapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                .build();
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                productionMapper, "state-key-1", new byte[32]);
        UUID requestId = UUID.randomUUID();
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification_nullable_size_1", "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_size", "数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                "conversation-1", new byte[32], "public-1", challenge,
                Map.of("field_size", Map.of("choice_size_2", "size:2")), Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, java.util.Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));

        AgentStatePayloadCodec.Envelope envelope = codec.encodeChallenge(
                requestId, "conversation-1", record);

        ClarificationStore.Record decoded = codec.decodeChallenge(
                requestId, "conversation-1", "clarification_nullable_size_1", envelope);
        assertThat(((BlockedGoalTemplate) decoded.resumeTemplate()).getRequestedSize()).isNull();
    }

    @Test void previousPayloadKeyRemainsReadableButNewWritesUseCurrentKey() {
        byte[] currentKey = new byte[32];
        java.util.Arrays.fill(currentKey, (byte) 1);
        byte[] previousKey = new byte[32];
        java.util.Arrays.fill(previousKey, (byte) 2);
        com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build();
        AgentStatePayloadCodec previous = new AgentStatePayloadCodec(
                mapper, "payload-v1", previousKey);
        AgentStatePayloadCodec current = new AgentStatePayloadCodec(
                mapper, "payload-v2", currentKey, Map.of("payload-v1", previousKey));
        UUID requestId = UUID.randomUUID();
        AgentStatePayloadCodec.SettlementPayload payload = new AgentStatePayloadCodec.SettlementPayload(
                new PublicAgentTurn.Conversational(requestId, "公开回答", List.of()),
                List.of(), List.of());

        AgentStatePayloadCodec.Envelope oldEnvelope = previous.encode(
                requestId, "conversation-1", payload);

        assertThat(current.decode(requestId, "conversation-1", oldEnvelope).publicTurn())
                .isInstanceOf(PublicAgentTurn.Conversational.class);
        assertThat(current.encode(requestId, "conversation-1", payload).keyId())
                .isEqualTo("payload-v2");
        assertThat(current.supportsKey("payload-v1")).isTrue();
        assertThat(current.supportsKey("payload-retired")).isFalse();
    }

    @Test void decodedCompleteSettlementDoesNotContainVisitorOrProviderSentinel() throws Exception {
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                JsonMapper.builder().addModule(new ParameterNamesModule())
                        .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build(),
                "state-key-1", new byte[32]);
        UUID requestId = UUID.randomUUID();
        String visitorQuestion = "访客隐私问题-sentinel-原文";
        String conversationWindow = "conversation-window-sentinel";
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification_privacy_1", "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_size", "数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                "conversation-1", new byte[32], "public-1", challenge,
                java.util.Map.of("field_size", java.util.Map.of(
                        "choice_size_2", "size:2")), java.util.Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, java.util.Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));
        ContinuationContext context = new com.portfolio.agent.turn.continuation.ProjectDiscussionContext(
                "context_handle_123", "conversation-1", "public-1",
                java.time.Instant.parse("2026-08-18T00:05:00Z"),
                "project-a", java.util.Set.of("project-a"),
                java.time.Instant.parse("2026-08-18T00:00:00Z"), null);
        PublicAgentTurn liveProviderTurn = new PublicAgentTurn.Conversational(
                requestId, visitorQuestion + " " + conversationWindow, List.of());
        PublicAgentTurn persistenceSafeTurn =
                new com.portfolio.agent.turn.lifecycle.PersistenceSafeReplayPolicy()
                        .forProviderBody(liveProviderTurn);
        AgentStatePayloadCodec.SettlementPayload payload = new AgentStatePayloadCodec.SettlementPayload(
                persistenceSafeTurn, List.of(context), List.of(record));

        AgentStatePayloadCodec.Envelope envelope = codec.encode(
                requestId, "conversation-1", payload);
        String ciphertextProbe = new String(
                envelope.ciphertext(), java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.ObjectMapper probeMapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build();
        AgentStatePayloadCodec.SettlementPayload decoded = codec.decode(
                requestId, "conversation-1", envelope);
        String completePlaintextProbe = probeMapper.writeValueAsString(decoded);

        assertThat(ciphertextProbe).doesNotContain(visitorQuestion, conversationWindow);
        assertThat(completePlaintextProbe).doesNotContain(visitorQuestion, conversationWindow);
        assertThat(decoded.publicTurn())
                .isInstanceOf(PublicAgentTurn.CapabilityUnavailable.class);
        assertThat(decoded.contexts()).hasSize(1);
        assertThat(decoded.challenges()).hasSize(1);
    }

    @Test void codecMapperRejectsMissingBlockedGoalCreatorFields() throws Exception {
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                JsonMapper.builder().addModule(new ParameterNamesModule()).build(),
                "state-key-1", new byte[32]);
        java.lang.reflect.Field mapperField = AgentStatePayloadCodec.class
                .getDeclaredField("mapper");
        mapperField.setAccessible(true);
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                (com.fasterxml.jackson.databind.ObjectMapper) mapperField.get(codec);

        assertThatThrownBy(() -> mapper.readValue("""
                {"templateKind":"BLOCKED_GOAL","goalKind":"PORTFOLIO_RECOMMEND","subjects":[],
                 "requestedOutputs":["RECOMMENDATION"],"facets":[],"dimensions":[],
                 "requestedSize":null,"constraints":[],"unresolvedField":"REQUESTED_SIZE",
                 "remainingFields":[],"depth":1}
                """, BlockedGoalTemplate.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class)
                .hasMessageContaining("askedFields");
    }

    @Test void optionalReplayShapesRoundTripWithoutGlobalMissingCreatorFailure() {
        AgentStatePayloadCodec codec = new AgentStatePayloadCodec(
                JsonMapper.builder().addModule(new ParameterNamesModule()).build(),
                "state-key-1", new byte[32]);
        UUID requestId = UUID.randomUUID();
        com.portfolio.agent.turn.projection.PublicSupport support =
                new com.portfolio.agent.turn.projection.PublicSupport(
                        com.portfolio.agent.turn.projection.PublicSupport.Kind.GENERAL_KNOWLEDGE,
                        List.of());
        com.portfolio.agent.turn.projection.PublicSection section =
                new com.portfolio.agent.turn.projection.PublicSection(
                        "section-one",
                        com.portfolio.agent.turn.execution.AnswerSectionType.GENERAL_PRINCIPLE,
                        "说明", "内容", support);
        com.portfolio.agent.turn.projection.AnswerGoalResult goal =
                new com.portfolio.agent.turn.projection.AnswerGoalResult(
                        "goal-one", "目标",
                        com.portfolio.agent.turn.projection.AnswerGoalResult.Coverage.FULL,
                        new com.portfolio.agent.turn.projection.PublicPresentation.Sectioned(
                                List.of(section)), List.of());
        com.portfolio.agent.turn.projection.PublicAnswer withoutLocal =
                new com.portfolio.agent.turn.projection.PublicAnswer(
                        com.portfolio.agent.turn.projection.PublicAnswer.Resolution.COMPLETE,
                        "public-1", List.of(goal),
                        new com.portfolio.agent.turn.projection.PublicSourceCatalog(List.of()),
                        List.of(com.portfolio.agent.turn.projection.PublicSupport.Kind.GENERAL_KNOWLEDGE),
                        List.of(), null);
        ClarificationChallenge local = new ClarificationChallenge(
                "clarification_local_1", "请选择", List.of(
                new ClarificationChallenge.TextField(
                        "field_local", "补充", true, 100)), List.of("goal-one"));
        com.portfolio.agent.turn.projection.PublicAnswer withLocal =
                new com.portfolio.agent.turn.projection.PublicAnswer(
                        com.portfolio.agent.turn.projection.PublicAnswer.Resolution.COMPLETE,
                        "public-1", List.of(goal),
                        new com.portfolio.agent.turn.projection.PublicSourceCatalog(List.of()),
                        List.of(com.portfolio.agent.turn.projection.PublicSupport.Kind.GENERAL_KNOWLEDGE),
                        List.of(), local);
        com.portfolio.agent.turn.projection.SuggestedAction action =
                new com.portfolio.agent.turn.projection.SuggestedAction(
                        "action-continue", "继续", null,
                        com.portfolio.agent.turn.continuation.ContinuationReference
                                .exitContext("context_handle_456"));

        assertRoundTrip(codec, requestId,
                new PublicAgentTurn.Answer(requestId, withoutLocal));
        assertRoundTrip(codec, requestId,
                new PublicAgentTurn.Answer(requestId, withLocal));
        assertRoundTrip(codec, requestId,
                new PublicAgentTurn.Conversational(
                        requestId, "固定公开会话文本", List.of()));
        assertRoundTrip(codec, requestId,
                new PublicAgentTurn.Clarification(
                        requestId, "固定澄清文本",
                        new ClarificationChallenge(
                                "clarification_variant_1", "请选择", List.of(
                                new ClarificationChallenge.TextField(
                                        "field_variant", "补充", true, 100)), List.of()),
                        List.of()));
        assertRoundTrip(codec, requestId,
                new PublicAgentTurn.Boundary(
                        requestId, "BOUNDARY", "边界", List.of(action)));
        assertRoundTrip(codec, requestId,
                new PublicAgentTurn.CapabilityUnavailable(
                        requestId, "FIXED_UNAVAILABLE", "固定不可用终局",
                        false, List.of()));
    }

    private void assertRoundTrip(
            AgentStatePayloadCodec codec, UUID requestId, PublicAgentTurn turn) {
        AgentStatePayloadCodec.Envelope envelope = codec.encode(
                requestId, "conversation-1",
                new AgentStatePayloadCodec.SettlementPayload(turn, List.of(), List.of()));
        assertThat(codec.decode(requestId, "conversation-1", envelope).publicTurn().getKind())
                .isEqualTo(turn.getKind());
    }

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
        com.portfolio.agent.turn.continuation.ProjectDiscussionContext context =
                new com.portfolio.agent.turn.continuation.ProjectDiscussionContext(
                "context_handle_123", "conversation-1", "public-1",
                java.time.Instant.parse("2026-08-18T00:05:00Z"),
                "project-a", java.util.Set.of("project-a"),
                java.time.Instant.parse("2026-08-18T00:00:00Z"), null);
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification_123", "请补充", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_detail", "推荐数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                "conversation-1", new byte[32], "public-1", challenge,
                java.util.Map.of("field_detail", java.util.Map.of(
                        "choice_size_2", "size:2")), java.util.Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, java.util.Set.of("BACKEND"),
                        ClarificationProposal.Field.REQUESTED_SIZE));
        AgentStatePayloadCodec.Envelope contextEnvelope =
                codec.encodeContext(requestId, "conversation-1", context);
        AgentStatePayloadCodec.Envelope challengeEnvelope =
                codec.encodeChallenge(requestId, "conversation-1", record);
        assertThat(codec.decodeContext(
                requestId, "conversation-1", "context_handle_123", contextEnvelope).getKind())
                .isEqualTo(com.portfolio.agent.turn.continuation.ContinuationContext.Kind.PROJECT_DISCUSSION);
        ClarificationStore.Record decoded = codec.decodeChallenge(
                requestId, "conversation-1", "clarification_123", challengeEnvelope);
        assertThat(decoded.challenge().getPrompt()).isEqualTo("请补充");
        BlockedGoalTemplate decodedGoal = (BlockedGoalTemplate) decoded.resumeTemplate();
        assertThat(decodedGoal.getGoalKind())
                .isEqualTo(com.portfolio.agent.turn.planning.GoalKind.PORTFOLIO_RECOMMEND);
        assertThat(decodedGoal.getConstraints()).containsExactly("BACKEND");
    }
}
