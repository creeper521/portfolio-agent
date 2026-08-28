package com.portfolio.agent.infrastructure.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProviderAttemptContextTest {

    @Test
    void exposesOnlyBoundedAttemptMetadataAndNeverRendersTheUuid() {
        UUID identity = UUID.fromString(
                "123e4567-e89b-12d3-a456-426614174000");
        ProviderAttemptContext context = new ProviderAttemptContext(
                identity, 2, 2, true);

        assertThat(context.attemptId()).isEqualTo(identity);
        assertThat(context.attemptIndex()).isEqualTo(2);
        assertThat(context.attemptCount()).isEqualTo(2);
        assertThat(context.duplicateBillingRisk()).isTrue();
        assertThat(context.toString())
                .contains("attemptIndex=2", "attemptCount=2",
                        "duplicateBillingRisk=true")
                .doesNotContain(identity.toString(), "attemptId");
    }

    @Test
    void rejectsImpossibleAttemptShapes() {
        UUID identity = UUID.randomUUID();

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProviderAttemptContext(identity, 0, 1, false));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProviderAttemptContext(identity, 2, 1, false));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProviderAttemptContext(identity, 1, 2, true));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProviderAttemptContext(identity, 2, 2, false));
    }

    @Test
    void attemptTimeoutCapIsPositiveInternalMetadataAndNeverRendered() {
        UUID identity = UUID.fromString(
                "123e4567-e89b-12d3-a456-426614174000");
        ProviderAttemptContext capped = new ProviderAttemptContext(
                identity, 1, 2, false, Duration.ofMillis(6_750));

        assertThat(capped.attemptTimeoutCap())
                .contains(Duration.ofMillis(6_750));
        assertThat(capped.toString())
                .doesNotContain("attemptTimeoutCap", "6750");
        assertThat(ProviderAttemptContext.single(UUID.randomUUID())
                .attemptTimeoutCap()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProviderAttemptContext(
                        identity, 1, 2, false, Duration.ZERO));
    }
}
