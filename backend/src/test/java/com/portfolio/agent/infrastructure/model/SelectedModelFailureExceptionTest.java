package com.portfolio.agent.infrastructure.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectedModelFailureExceptionTest {
    @Test
    void transportFailuresMapToTheFiveClosedPublicSemantics() {
        assertMapping(
                StructuredModelFailure.Code.DEADLINE_EXCEEDED,
                SelectedModelFailureException.Code.SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
                true, null);
        assertMapping(
                StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                SelectedModelFailureException.Code.SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
                true, null);
        assertMapping(
                StructuredModelFailure.Code.PROVIDER_UNAVAILABLE,
                SelectedModelFailureException.Code.SELECTED_MODEL_TEMPORARILY_UNAVAILABLE,
                true, null);
        assertMapping(
                StructuredModelFailure.Code.AUTHENTICATION_REJECTED,
                SelectedModelFailureException.Code.SELECTED_MODEL_UNAVAILABLE,
                false, null);
        assertMapping(
                StructuredModelFailure.Code.BILLING_REJECTED,
                SelectedModelFailureException.Code.SELECTED_MODEL_UNAVAILABLE,
                false, null);
        assertMapping(
                StructuredModelFailure.Code.PROVIDER_REJECTED,
                SelectedModelFailureException.Code.SELECTED_MODEL_UNAVAILABLE,
                false, null);
        assertMapping(
                StructuredModelFailure.Code.RESPONSE_TOO_LARGE,
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE,
                false, null);
        assertMapping(
                StructuredModelFailure.Code.RESPONSE_JSON_INVALID,
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE,
                false, null);
        assertMapping(
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID,
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE,
                false, null);
        assertMapping(
                StructuredModelFailure.Code.INVALID_RESPONSE,
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE,
                false, null);
    }

    @Test
    void providerRateLimitCarriesOnlyABoundedRetryDelay() {
        StructuredModelFailure source = new StructuredModelFailure(
                StructuredModelFailure.Code.RATE_LIMITED, 300, null);

        SelectedModelFailureException failure =
                SelectedModelFailureException.from(source);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_RATE_LIMITED);
        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getRetryAfterSeconds()).isEqualTo(300);
        assertThat(failure.isAttempted()).isTrue();
        assertThat(failure.getMessage()).isEqualTo("selected model operation failed");

        SelectedModelFailureException defaulted =
                SelectedModelFailureException.from(new StructuredModelFailure(
                        StructuredModelFailure.Code.RATE_LIMITED));
        assertThat(defaulted.getRetryAfterSeconds()).isEqualTo(30);

        SelectedModelFailureException immediate =
                SelectedModelFailureException.from(
                        StructuredModelFailure.rateLimited(
                                429, 0,
                                StructuredModelFailure.RetryAfterDisposition.VALID));
        assertThat(immediate.getRetryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void invalidModelOutputNeverCarriesTheInternalCauseMessage() {
        SelectedModelFailureException failure =
                SelectedModelFailureException.invalidResponse(
                        new IllegalArgumentException(
                                "endpoint=https://internal.invalid key=secret provider-body"));

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code.SELECTED_MODEL_INVALID_RESPONSE);
        assertThat(failure.isRetryable()).isFalse();
        assertThat(failure.getRetryAfterSeconds()).isNull();
        assertThat(failure.isAttempted()).isTrue();
        assertThat(failure.getMessage())
                .doesNotContain("endpoint", "secret", "provider-body");
    }

    @Test
    void preAttemptDeadlineIsRetryableWithoutClaimingAProviderAttempt() {
        SelectedModelFailureException failure = SelectedModelFailureException
                .temporarilyUnavailableBeforeAttempt();

        assertThat(failure.getCode()).isEqualTo(SelectedModelFailureException.Code
                .SELECTED_MODEL_TEMPORARILY_UNAVAILABLE);
        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.isAttempted()).isFalse();
        assertThat(failure.getRetryAfterSeconds()).isNull();
    }

    private void assertMapping(
            StructuredModelFailure.Code source,
            SelectedModelFailureException.Code expected,
            boolean retryable,
            Integer retryAfterSeconds) {
        SelectedModelFailureException failure = SelectedModelFailureException.from(
                new StructuredModelFailure(source));

        assertThat(failure.getCode()).isEqualTo(expected);
        assertThat(failure.isRetryable()).isEqualTo(retryable);
        assertThat(failure.getRetryAfterSeconds()).isEqualTo(retryAfterSeconds);
        assertThat(failure.isAttempted()).isTrue();
    }
}
