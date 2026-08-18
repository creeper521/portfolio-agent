package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralDraftCodecAdversarialTest {
    private final GeneralDraftCodec codec = new GeneralDraftCodec(new ObjectMapper());

    @Test void rejectsUnknownRootField() {
        assertThatThrownBy(() -> codec.decode(
                GeneralTestFixtures.VALID_EXPLANATION.replace(
                        "\"caveats\":", "\"secretEvidence\":[],\"caveats\":")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownRoleAndNonTextCaveat() {
        assertThatThrownBy(() -> codec.decode(
                GeneralTestFixtures.VALID_EXPLANATION.replace("DEFINITION", "FACT_VERIFIED")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(
                GeneralTestFixtures.VALID_EXPLANATION.replace(
                        "\"具体机制取决于运行环境。\"", "{\"raw\":true}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
