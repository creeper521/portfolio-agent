package com.portfolio.agent.common.observability;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticEventTest {

    @Test
    void permitsOnlyClosedRetrySchedulingMetadata() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                        "provider.call.retry_scheduled", DiagnosticLevel.INFO)
                .field("attempt.index", 2)
                .field("attempt.count", 2)
                .field("failure.code", "PROVIDER_UNAVAILABLE")
                .field("wait.bucket", "NO_WAIT")
                .build();

        assertThat(event.getFields()).containsOnlyKeys(
                "attempt.index", "attempt.count", "failure.code", "wait.bucket");
        assertThatIllegalArgumentException().isThrownBy(() -> DiagnosticEvent.builder(
                        "provider.call.retry_scheduled", DiagnosticLevel.INFO)
                .field("provider.attempt_id", "private-uuid"));
        assertThatIllegalArgumentException().isThrownBy(() -> DiagnosticEvent.builder(
                        "provider.call.retry_scheduled", DiagnosticLevel.INFO)
                .field("provider.operation", "GENERAL_KNOWLEDGE"));
    }

    @Test
    void permitsOnlyLowCardinalityAttemptAndUsageMetadataOnProviderCalls() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                        "provider.call.completed", DiagnosticLevel.INFO)
                .field("provider.operation", "GENERAL_KNOWLEDGE")
                .field("event.outcome", "SUCCESS")
                .field("duration.bucket", "LT_100_MS")
                .field("response.present", true)
                .field("attempt.index", 2)
                .field("attempt.count", 2)
                .field("duplicate.billing.risk", true)
                .field("usage.present", true)
                .field("usage.input_tokens.bucket", "FROM_1_TO_255")
                .field("usage.output_tokens.bucket", "FROM_256_TO_1023")
                .field("usage.total_tokens.bucket", "FROM_1024_TO_4095")
                .build();

        assertThat(event.getFields()).containsOnlyKeys(
                "provider.operation", "event.outcome", "duration.bucket",
                "response.present", "attempt.index", "attempt.count",
                "duplicate.billing.risk", "usage.present",
                "usage.input_tokens.bucket", "usage.output_tokens.bucket",
                "usage.total_tokens.bucket");
        assertThatIllegalArgumentException().isThrownBy(() ->
                DiagnosticEvent.builder(
                                "provider.call.completed", DiagnosticLevel.INFO)
                        .field("provider.attempt_id", "private-uuid"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                DiagnosticEvent.builder(
                                "provider.call.completed", DiagnosticLevel.INFO)
                        .field("usage.cost", "private-cost"));
    }

    @Test
    void permitsOnlyClosedGeneralAdmissionMetadata() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                        "provider.output.admitted", DiagnosticLevel.INFO)
                .field("provider.operation", "GENERAL_KNOWLEDGE")
                .field("admission.level", "NORMALIZED")
                .field("normalization.rule", "UNKNOWN_FIELD_COUNT")
                .field("normalization.count", 2)
                .build();

        assertThat(event.getFields())
                .containsEntry("admission.level", "NORMALIZED")
                .containsEntry("normalization.rule", "UNKNOWN_FIELD_COUNT")
                .containsEntry("normalization.count", 2)
                .doesNotContainKeys("unknown.field", "unknown.value");
        assertThatIllegalArgumentException().isThrownBy(() -> DiagnosticEvent.builder(
                        "provider.output.admitted", DiagnosticLevel.INFO)
                .field("unknown.field", "private-provider-key"));
    }

    @Test
    void rejectsForbiddenFieldNames() {
        assertThatThrownBy(() -> DiagnosticEvent.builder(
                "http.request.failed", DiagnosticLevel.ERROR)
                .field("visitor.question", "secret")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("diagnostic field is forbidden: visitor.question");
    }

    @Test
    void eventCopiesOnlyApprovedScalarValues() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                "http.request.completed", DiagnosticLevel.INFO)
                .field("http.status_code", 200)
                .field("duration.ms", 12)
                .build();

        assertThat(event.getSchemaVersion()).isEqualTo(1);
        assertThat(event.getName()).isEqualTo("http.request.completed");
        assertThat(event.getFields()).containsEntry("http.status_code", 200);
        assertThat(event.getFields()).doesNotContainKey("message");
    }

    @Test
    void rejectsInvalidEventNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> DiagnosticEvent.builder(
                "Http.request.completed", DiagnosticLevel.INFO));
    }

    @Test
    void rejectsUnknownWellFormedEventNamesBeforeTheyReachTheAdapter() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiagnosticEvent.builder(
                        "visitor.secret", DiagnosticLevel.INFO))
                .withMessage("unsupported diagnostic event name: visitor.secret");
    }

    @Test
    void convertsEnumValuesToNames() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                "provider.call.completed", DiagnosticLevel.INFO)
                .field("event.outcome", Outcome.SUCCESS)
                .build();

        assertThat(event.getFields()).containsEntry("event.outcome", "SUCCESS");
    }

    @Test
    void permitsEveryExplicitlyApprovedAnswerStateKey() {
        Map<String, Object> approvedFields = Map.of(
                "answer.resolution", Outcome.SUCCESS,
                "answer.source", "DETERMINISTIC",
                "answer.scope", "PORTFOLIO");

        for (Map.Entry<String, Object> approvedField : approvedFields.entrySet()) {
            DiagnosticEvent event = DiagnosticEvent.builder(
                    "agent.request.completed", DiagnosticLevel.INFO)
                    .field(approvedField.getKey(), approvedField.getValue())
                    .build();

            assertThat(event.getFields()).containsKey(approvedField.getKey());
        }

        Map<String, Object> approvedStartupFields = Map.of(
                "answer.request_timeout_ms", 30_000,
                "answer.requests_per_minute", 60,
                "answer.max_concurrent", 4);

        for (Map.Entry<String, Object> approvedField : approvedStartupFields.entrySet()) {
            DiagnosticEvent event = DiagnosticEvent.builder(
                    "application.started", DiagnosticLevel.INFO)
                    .field(approvedField.getKey(), approvedField.getValue())
                    .build();

            assertThat(event.getFields()).containsKey(approvedField.getKey());
        }
    }

    @Test
    void permitsQuestionKindButRejectsQuestionText() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                "agent.request.completed", DiagnosticLevel.INFO)
                .field("question.kind", "FREE_TEXT")
                .build();

        assertThat(event.getFields()).containsEntry("question.kind", "FREE_TEXT");
        assertThatIllegalArgumentException().isThrownBy(() -> DiagnosticEvent.builder(
                "agent.request.completed", DiagnosticLevel.INFO)
                .field("question.text", "private visitor question"));
    }

    @Test
    void rejectsUnapprovedAnswerFields() {
        List<String> unapprovedFields = List.of(
                "answer.degrade",
                "answer.resolutions",
                "answer.content");

        for (String unapprovedField : unapprovedFields) {
            assertThatIllegalArgumentException()
                    .as(unapprovedField)
                    .isThrownBy(() -> DiagnosticEvent.builder(
                            "agent.request.completed", DiagnosticLevel.INFO)
                            .field(unapprovedField, "private answer text"));
        }
    }

    @Test
    void rejectsSensitiveSemanticFieldNameVariants() {
        List<String> forbiddenKeys = List.of(
                "http.header",
                "http.headers",
                "request.body",
                "request.credentials",
                "request.credential",
                "request.authorization",
                "request.cookie",
                "client.ip",
                "client.raw_ip",
                "client.raw-ip",
                "client.rawIp",
                "http.request_body",
                "http.response_body",
                "request.credential-value",
                "http.header-value",
                "request.body-content",
                "visitor-question",
                "responseMessage",
                "modelPrompt",
                "provider-payload",
                "answersText",
                "visitorQuestions",
                "providerPayloads",
                "responseMessages",
                "auditquestion",
                "preanswerText",
                "clientrawip");

        for (String forbiddenKey : forbiddenKeys) {
            assertThatIllegalArgumentException()
                    .as(forbiddenKey)
                    .isThrownBy(() -> DiagnosticEvent.builder(
                            "http.request.completed", DiagnosticLevel.INFO)
                            .field(forbiddenKey, "secret"));
        }
    }

    @Test
    void permitsSafeRequestIdentifierKeys() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                "http.request.completed", DiagnosticLevel.INFO)
                .field("request.id", "request-id")
                .field("client.request.id", "client-request-id")
                .build();

        assertThat(event.getFields())
                .containsEntry("request.id", "request-id")
                .containsEntry("client.request.id", "client-request-id");
    }

    @Test
    void rejectsFieldsOutsideTheClosedEventContract() {
        List<String> unapprovedKeys = List.of(
                "visitor.text",
                "pipeline",
                "requestId",
                "failure.frames");

        for (String key : unapprovedKeys) {
            assertThatIllegalArgumentException()
                    .as(key)
                    .isThrownBy(() -> DiagnosticEvent.builder(
                            "http.request.completed", DiagnosticLevel.INFO)
                            .field(key, "PRIVATE_VISITOR_SENTINEL"))
                    .withMessage("diagnostic field is not approved for "
                            + "http.request.completed: " + key);
        }
    }

    @Test
    void rejectsMutableNumberValues() {
        List<Number> mutableNumbers = List.of(new AtomicInteger(1), new AtomicLong(2L));

        for (Number mutableNumber : mutableNumbers) {
            assertThatIllegalArgumentException()
                    .as(mutableNumber.getClass().getSimpleName())
                    .isThrownBy(() -> DiagnosticEvent.builder(
                            "http.request.completed", DiagnosticLevel.INFO)
                            .field("duration.ms", mutableNumber));
        }
    }

    @Test
    void permitsImmutableNumberValues() {
        List<Number> immutableNumbers = List.of(
                Byte.valueOf((byte) 1),
                Short.valueOf((short) 2),
                Integer.valueOf(3),
                Long.valueOf(4L),
                Float.valueOf(5.0F),
                Double.valueOf(6.0D),
                BigInteger.valueOf(7L),
                BigDecimal.valueOf(8L));

        for (Number immutableNumber : immutableNumbers) {
            DiagnosticEvent event = DiagnosticEvent.builder(
                            "http.request.completed", DiagnosticLevel.INFO)
                    .field("duration.ms", immutableNumber)
                    .build();
            assertThat(event.getFields()).containsEntry("duration.ms", immutableNumber);
        }
    }

    @Test
    void rejectsUnsupportedFieldValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> DiagnosticEvent.builder(
                "http.request.completed", DiagnosticLevel.INFO)
                .field("duration.ms", new Object()));
    }

    @Test
    void exposesAnUnmodifiableFieldCopy() {
        DiagnosticEvent event = DiagnosticEvent.builder(
                "http.request.completed", DiagnosticLevel.INFO)
                .field("http.status_code", 200)
                .build();

        assertThatThrownBy(() -> event.getFields().put("event.outcome", "success"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private enum Outcome {
        SUCCESS
    }
}
