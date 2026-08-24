package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralModelOutputDiagnosticsTest {

    @Test
    void codecAndValidatorRejectionsHaveDistinctSafeLayers() {
        assertLayer("{}", "SCHEMA", "OUTPUT_SCHEMA_REJECTED");
        assertLayer(GeneralTestFixtures.VALID_EXPLANATION.replace(
                "\"topic\":\"并发控制\"", "\"topic\":\"其他主题\""),
                "SEMANTIC", "OUTPUT_SEMANTIC_REJECTED");
    }

    private void assertLayer(String output, String layer, String code) {
        List<DiagnosticEvent> events = new ArrayList<>();
        GeneralKnowledgeGenerator generator = new GeneralKnowledgeGenerator(
                request -> output,
                new GeneralDraftCodec(new ObjectMapper()),
                new GeneralDraftValidator(),
                new ModelOutputDiagnostics(events::add));

        assertThatThrownBy(() -> generator.generate(GeneralTestFixtures.explanation()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("provider.output.rejected");
            assertThat(event.getFields().get("provider.operation"))
                    .isEqualTo("GENERAL_KNOWLEDGE");
            assertThat(event.getFields().get("failure.layer")).isEqualTo(layer);
            assertThat(event.getFields().get("failure.code")).isEqualTo(code);
            if (layer.equals("SEMANTIC")) {
                assertThat(event.getFields().get("failure.reason"))
                        .isEqualTo("TOPIC_MISMATCH");
            } else {
                assertThat(event.getFields()).doesNotContainKey("failure.reason");
            }
            assertThat(event.toString()).doesNotContain(output);
        });
    }
}
