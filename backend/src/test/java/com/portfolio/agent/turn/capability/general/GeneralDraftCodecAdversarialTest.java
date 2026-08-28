package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralDraftCodecAdversarialTest {
    private final GeneralDraftCodec codec = new GeneralDraftCodec(new ObjectMapper());
    private final StructuredOutputContractRegistry contracts =
            StructuredOutputContractRegistry.standard();

    @Test
    void onlyValidatedCarrierDecodeIsPublicAuthority() {
        assertThat(Arrays.stream(GeneralDraftCodec.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("decode"))
                .filter(method -> Modifier.isPublic(method.getModifiers())))
                .singleElement()
                .satisfies(method -> assertThat(method.getParameterTypes())
                        .containsExactly(StructurallyValidatedOutput.class));
    }

    @Test
    void decodesOnlyValidatedGeneralCanonicalV2OrV3Trees() {
        StructurallyValidatedOutput v2 = contracts.validate(
                new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                        "general.draft.v2"),
                GeneralTestFixtures.VALID_EXPLANATION);
        StructurallyValidatedOutput v3 = contracts.validate(
                new StructuredContractRef(ModelOperation.GENERAL_KNOWLEDGE,
                        "general.draft.v3"), """
                        {"topic":"并发控制","statements":[
                          {"role":"DEFINITION","text":"定义。","subject":null,
                           "dimension":null,"aspects":["DEFINITION"]},
                          {"role":"MECHANISM","text":"机制。","subject":null,
                           "dimension":null,"aspects":["MECHANISM"]}],"caveats":[]}
                        """);

        assertThat(codec.decode(v2).topic()).isEqualTo("并发控制");
        assertThat(codec.decode(v3).statements()).hasSize(2);

        StructurallyValidatedOutput wrongOperation = contracts.validate(
                new StructuredContractRef(ModelOperation.TURN_INTERPRETATION,
                        "goal.proposal.v5"),
                "{\"kind\":\"CONVERSATIONAL\",\"message\":\"请说明目标\"}");
        assertThatThrownBy(() -> codec.decode(wrongOperation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contract");
    }

    @Test void rejectsTrailingJsonValue() {
        assertThatThrownBy(() -> codec.decode(
                GeneralTestFixtures.VALID_EXPLANATION + " {}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

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
                        "{\"kind\":\"RISK\",\"text\":\"错误的锁策略可能降低吞吐量。\"}",
                        "{\"raw\":true}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsCaveatBeyondTheBoundedTextLimit() {
        String oversized = "边".repeat(1000) + "。";
        assertThatThrownBy(() -> codec.decode(
                GeneralTestFixtures.VALID_EXPLANATION.replace(
                        "错误的锁策略可能降低吞吐量。", oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caveat text");
    }
}
