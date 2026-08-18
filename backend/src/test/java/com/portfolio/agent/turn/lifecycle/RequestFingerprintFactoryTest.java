package com.portfolio.agent.turn.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintFactoryTest {
    private final RequestFingerprintFactory factory =
            new RequestFingerprintFactory(new byte[32]);

    @Test void sameClosedCommandIsStableAcrossRequestIds() {
        byte[] first = factory.fingerprint(new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("介绍项目"), null, null));
        byte[] second = factory.fingerprint(new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("介绍项目"), null, null));
        assertThat(first).containsExactly(second);
    }

    @Test void commandKindAndLengthFramingPreventAmbiguousCollisions() {
        byte[] ask = factory.fingerprint(new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("abc"), null, null));
        byte[] continuation = factory.fingerprint(new AgentTurnCommand.Continue(
                UUID.randomUUID(), "context1", null, "abc", null, null));
        assertThat(Arrays.equals(ask, continuation)).isFalse();
    }
}
