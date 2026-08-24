package com.portfolio.agent.turn.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintFactoryTest {
    private final RequestFingerprintFactory factory =
            new RequestFingerprintFactory(new byte[32]);

    @Test void sameClosedCommandIsStableAcrossRequestIds() {
        byte[] first = factory.fingerprint(new AgentTurnCommand.Ask(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("介绍项目"), null, null));
        byte[] second = factory.fingerprint(new AgentTurnCommand.Ask(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("介绍项目"), null, null));
        assertThat(first).containsExactly(second);
    }

    @Test void commandKindAndLengthFramingPreventAmbiguousCollisions() {
        byte[] ask = factory.fingerprint(new AgentTurnCommand.Ask(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("abc"), null, null));
        byte[] continuation = factory.fingerprint(new AgentTurnCommand.Continue(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                AgentTurnCommand.ContinueOperation.ROUTE_IN_CONTEXT,
                "context1", null, "abc", null, null, null));
        assertThat(Arrays.equals(ask, continuation)).isFalse();
    }

    @Test void surfaceAndConversationWindowArePartOfIdempotencyIdentity() {
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand.Ask plain = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("介绍项目"), null, null);
        AgentTurnCommand.Ask scoped = new AgentTurnCommand.Ask(
                requestId, AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("介绍项目"),
                new AgentTurnCommand.SurfaceContext(
                        new AgentTurnCommand.SubjectHint(
                                AgentTurnCommand.SubjectHintKind.PROJECT, "project-a"),
                        AgentTurnCommand.AudienceRole.GUEST,
                        AgentTurnCommand.RequestSource.PROJECT),
                new ConversationWindow(List.of(new ConversationWindow.Message(
                        ConversationWindow.Role.USER, "前文"))));
        assertThat(Arrays.equals(factory.fingerprint(plain), factory.fingerprint(scoped))).isFalse();
    }

    @Test void keyringAcceptsOnePreviousFingerprintButWritesCurrent() {
        byte[] previous = new byte[32];
        java.util.Arrays.fill(previous, (byte) 1);
        byte[] current = new byte[32];
        java.util.Arrays.fill(current, (byte) 2);
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), AgentTurnCommand.ModelSelection.none(),
                new AgentTurnCommand.FreeText("介绍项目"), null, null);
        byte[] oldFingerprint = new RequestFingerprintFactory(previous).fingerprint(command);

        RequestFingerprintSet rotated = new RequestFingerprintFactory(
                current, List.of(previous)).fingerprints(command);

        assertThat(rotated.matches(oldFingerprint)).isTrue();
        assertThat(rotated.current()).containsExactly(
                new RequestFingerprintFactory(current).fingerprint(command));
    }

    @Test void modelSelectionIsPartOfIdempotencyIdentity() {
        UUID requestId = UUID.randomUUID();
        AgentTurnCommand.Ask glm = ask(
                requestId, AgentTurnCommand.ModelSelection.model(
                        "glm-4-7-flash", "glm-4-7-flash-v1"));
        AgentTurnCommand.Ask qwen = ask(
                requestId, AgentTurnCommand.ModelSelection.model(
                        "qwen-3-7-flash", "qwen-3-7-flash-v1"));
        AgentTurnCommand.Ask newerGlm = ask(
                requestId, AgentTurnCommand.ModelSelection.model(
                        "glm-4-7-flash", "glm-4-7-flash-v2"));
        AgentTurnCommand.Ask none = ask(
                requestId, AgentTurnCommand.ModelSelection.none());

        assertThat(Arrays.equals(factory.fingerprint(glm), factory.fingerprint(qwen))).isFalse();
        assertThat(Arrays.equals(factory.fingerprint(glm), factory.fingerprint(newerGlm))).isFalse();
        assertThat(Arrays.equals(factory.fingerprint(glm), factory.fingerprint(none))).isFalse();
    }

    private AgentTurnCommand.Ask ask(
            UUID requestId, AgentTurnCommand.ModelSelection modelSelection) {
        return new AgentTurnCommand.Ask(
                requestId, modelSelection, new AgentTurnCommand.FreeText("介绍项目"), null, null);
    }
}
