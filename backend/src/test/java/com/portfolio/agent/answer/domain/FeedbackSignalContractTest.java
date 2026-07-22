package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackSignalContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Test
    void acceptsOnlyTheApprovedAnonymousDimensions() throws Exception {
        FeedbackSignal signal = mapper.readValue("""
                {"contentVersion":"2026-07-21.1","questionPresetId":"sql-audit-overview",
                "resolution":"ANSWERED","answerSource":"PRESET","helpful":false,
                "reason":"EVIDENCE_INSUFFICIENT"}
                """, FeedbackSignal.class);

        assertThat(signal.getReason()).isEqualTo(FeedbackReason.EVIDENCE_INSUFFICIENT);
        assertThat(mapper.writeValueAsString(signal)).doesNotContain("\"question\":", "answerText");
    }

    @Test
    void rejectsFreeTextAndUnknownReasons() {
        assertThatThrownBy(() -> mapper.readValue("""
                {"contentVersion":"v1","questionPresetId":"preset-1","resolution":"ANSWERED",
                "answerSource":"PRESET","helpful":false,"reason":"INCORRECT",
                "comment":"visitor free text"}
                """, FeedbackSignal.class)).hasMessageContaining("comment");

        assertThatThrownBy(() -> mapper.readValue("""
                {"contentVersion":"v1","questionPresetId":"preset-1","resolution":"ANSWERED",
                "answerSource":"PRESET","helpful":false,"reason":"CUSTOM"}
                """, FeedbackSignal.class)).hasMessageContaining("CUSTOM");
    }
}
