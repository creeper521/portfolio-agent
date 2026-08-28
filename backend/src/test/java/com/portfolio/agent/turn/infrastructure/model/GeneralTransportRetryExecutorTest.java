package com.portfolio.agent.turn.infrastructure.model;

import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.ProviderAttemptContext;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.infrastructure.model.StructuredModelRequest;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputCompiler;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.turn.execution.TurnDeadline;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralTransportRetryExecutorTest {

    @Test
    void defaultTenSecondBudgetCapsOnlyTheFirstAttemptAndKeepsThreeSeconds() {
        MutableClock clock = new MutableClock();
        StructuredOutputGateway gateway = mock(StructuredOutputGateway.class);
        ModelTransportBinding binding = mock(ModelTransportBinding.class);
        StructuredModelRequest request = request(
                clock, Duration.ofSeconds(10));
        StructuredOutputCompiler compiler = compiler();
        StructurallyValidatedOutput output = StructuredModelTestFixtures
                .validatedGeneral("""
                        {"topic":"并发控制","statements":[
                          {"role":"DEFINITION","text":"定义。",
                           "subject":null,"dimension":null,
                           "aspects":["DEFINITION"]},
                          {"role":"MECHANISM","text":"机制。",
                           "subject":null,"dimension":null,
                           "aspects":["MECHANISM"]}],"caveats":[]}
                        """);
        List<ProviderAttemptContext> contexts = new ArrayList<>();
        when(gateway.execute(
                same(binding), same(request), same(compiler),
                any(ProviderAttemptContext.class)))
                .thenAnswer(invocation -> {
                    ProviderAttemptContext context = invocation.getArgument(3);
                    contexts.add(context);
                    if (context.attemptIndex() == 1) {
                        Duration cap = context.attemptTimeoutCap().orElseThrow();
                        clock.advance(cap.toMillis());
                        throw StructuredModelFailure.deadline(
                                StructuredModelFailure.TimeoutDisposition
                                        .NO_RESPONSE,
                                null);
                    }
                    return output;
                });
        AtomicInteger sequence = new AtomicInteger();
        GeneralTransportRetryExecutor executor =
                new GeneralTransportRetryExecutor(
                        gateway, millis -> clock.advance(millis),
                        () -> 175,
                        () -> new UUID(0L, sequence.incrementAndGet()));

        assertThat(executor.execute(binding, request, compiler))
                .isSameAs(output);

        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0).attemptTimeoutCap())
                .contains(Duration.ofMillis(6_750));
        assertThat(contexts.get(1).attemptTimeoutCap()).isEmpty();
        assertThat(request.deadline().remainingMillis())
                .isGreaterThanOrEqualTo(
                        GeneralTransportRetryExecutor
                                .MINIMUM_RETRY_BUDGET_MILLIS)
                .isEqualTo(3_250L);
    }

    @Test
    void insufficientInitialBudgetIsNotArtificiallyCapped() {
        MutableClock clock = new MutableClock();
        StructuredOutputGateway gateway = mock(StructuredOutputGateway.class);
        ModelTransportBinding binding = mock(ModelTransportBinding.class);
        StructuredModelRequest request = request(
                clock, Duration.ofMillis(3_250));
        StructuredOutputCompiler compiler = compiler();
        List<ProviderAttemptContext> contexts = new ArrayList<>();
        when(gateway.execute(
                same(binding), same(request), same(compiler),
                any(ProviderAttemptContext.class)))
                .thenAnswer(invocation -> {
                    ProviderAttemptContext context = invocation.getArgument(3);
                    contexts.add(context);
                    assertThat(context.attemptTimeoutCap()).isEmpty();
                    clock.advance(3_250L);
                    throw StructuredModelFailure.deadline(
                            StructuredModelFailure.TimeoutDisposition.NO_RESPONSE,
                            null);
                });
        GeneralTransportRetryExecutor executor =
                new GeneralTransportRetryExecutor(
                        gateway, millis -> clock.advance(millis),
                        () -> 175, UUID::randomUUID);

        assertThatThrownBy(() -> executor.execute(
                binding, request, compiler))
                .isInstanceOf(StructuredModelFailure.class);

        assertThat(contexts).hasSize(1);
        assertThat(request.deadline().remainingMillis()).isZero();
    }

    @Test
    void successCanCompleteInsideTheFirstAttemptCapWithoutRetry() {
        MutableClock clock = new MutableClock();
        StructuredOutputGateway gateway = mock(StructuredOutputGateway.class);
        ModelTransportBinding binding = mock(ModelTransportBinding.class);
        StructuredModelRequest request = request(
                clock, Duration.ofSeconds(10));
        StructuredOutputCompiler compiler = compiler();
        StructurallyValidatedOutput output = StructuredModelTestFixtures
                .validatedGeneral("""
                        {"topic":"并发控制","statements":[
                          {"role":"DEFINITION","text":"定义。",
                           "subject":null,"dimension":null,
                           "aspects":["DEFINITION"]},
                          {"role":"MECHANISM","text":"机制。",
                           "subject":null,"dimension":null,
                           "aspects":["MECHANISM"]}],"caveats":[]}
                        """);
        List<ProviderAttemptContext> contexts = new ArrayList<>();
        when(gateway.execute(
                same(binding), same(request), same(compiler),
                any(ProviderAttemptContext.class)))
                .thenAnswer(invocation -> {
                    ProviderAttemptContext context = invocation.getArgument(3);
                    contexts.add(context);
                    clock.advance(context.attemptTimeoutCap()
                            .orElseThrow().toMillis() - 1L);
                    return output;
                });
        GeneralTransportRetryExecutor executor =
                new GeneralTransportRetryExecutor(
                        gateway, millis -> clock.advance(millis),
                        () -> 175, UUID::randomUUID);

        assertThat(executor.execute(binding, request, compiler))
                .isSameAs(output);
        assertThat(contexts).hasSize(1);
        assertThat(request.deadline().remainingMillis()).isEqualTo(3_251L);
    }

    @Test
    void retriesOnlyApprovedTransientTransportFailuresOnceWithFrozenArguments() {
        List<StructuredModelFailure> failures = List.of(
                StructuredModelFailure.connection(
                        new java.net.ConnectException("test connection")),
                StructuredModelFailure.deadline(
                        StructuredModelFailure.TimeoutDisposition.NO_RESPONSE,
                        null),
                StructuredModelFailure.http(
                        StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 502),
                StructuredModelFailure.http(
                        StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 503),
                StructuredModelFailure.http(
                        StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 504));

        for (StructuredModelFailure firstFailure : failures) {
            Fixture fixture = fixture(Duration.ofSeconds(10), 175);
            when(fixture.gateway.execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler)))
                    .thenThrow(firstFailure)
                    .thenReturn(fixture.output);

            assertThat(fixture.executor.execute(
                    fixture.binding, fixture.request, fixture.compiler))
                    .isSameAs(fixture.output);

            verify(fixture.gateway, times(2)).execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler));
            ArgumentCaptor<ProviderAttemptContext> contexts =
                    ArgumentCaptor.forClass(ProviderAttemptContext.class);
            verify(fixture.gateway, times(2)).execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler), contexts.capture());
            assertThat(contexts.getAllValues())
                    .extracting(
                            ProviderAttemptContext::attemptIndex,
                            ProviderAttemptContext::attemptCount,
                            ProviderAttemptContext::duplicateBillingRisk)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1, 2, false),
                            org.assertj.core.groups.Tuple.tuple(2, 2, true));
            assertThat(contexts.getAllValues())
                    .extracting(ProviderAttemptContext::attemptId)
                    .containsExactlyElementsOf(fixture.attemptIds);
            assertThat(contexts.getAllValues().get(0).attemptTimeoutCap())
                    .contains(Duration.ofMillis(6_750));
            assertThat(contexts.getAllValues().get(1).attemptTimeoutCap())
                    .isEmpty();
            assertThat(fixture.attemptIds).hasSize(2).doesNotHaveDuplicates();
            assertThat(fixture.events).singleElement().satisfies(event -> {
                assertThat(event.getFields().get("failure.code"))
                        .isEqualTo(firstFailure.getCode().name());
                assertThat(event.getFields().get("wait.bucket"))
                        .isEqualTo("NO_WAIT");
            });
        }
    }

    @Test
    void missingRetryAfterUsesOnlyTheApprovedJitterRange() {
        for (int jitter : List.of(100, 250)) {
            Fixture fixture = fixture(Duration.ofSeconds(10), jitter);
            when(fixture.gateway.execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler)))
                    .thenThrow(StructuredModelFailure.rateLimited(
                            429, null,
                            StructuredModelFailure.RetryAfterDisposition.MISSING))
                    .thenReturn(fixture.output);

            fixture.executor.execute(
                    fixture.binding, fixture.request, fixture.compiler);

            assertThat(fixture.sleeps).containsExactly((long) jitter);
            verify(fixture.gateway, times(2)).execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler));
        }
    }

    @Test
    void retryAfterOneSecondWaitsButLongOrInvalidValuesDoNotRetry() {
        Fixture immediate = fixture(Duration.ofSeconds(10), 175);
        when(immediate.gateway.execute(
                same(immediate.binding), same(immediate.request),
                same(immediate.compiler)))
                .thenThrow(StructuredModelFailure.rateLimited(
                        429, 0,
                        StructuredModelFailure.RetryAfterDisposition.VALID))
                .thenReturn(immediate.output);

        immediate.executor.execute(
                immediate.binding, immediate.request, immediate.compiler);

        assertThat(immediate.sleeps).isEmpty();
        assertThat(immediate.events).singleElement().satisfies(event ->
                assertThat(event.getFields().get("wait.bucket"))
                        .isEqualTo("RETRY_AFTER_LE_1S"));
        verify(immediate.gateway, times(2)).execute(
                same(immediate.binding), same(immediate.request),
                same(immediate.compiler));

        Fixture oneSecond = fixture(Duration.ofSeconds(10), 175);
        when(oneSecond.gateway.execute(
                same(oneSecond.binding), same(oneSecond.request),
                same(oneSecond.compiler)))
                .thenThrow(StructuredModelFailure.rateLimited(
                        429, 1,
                        StructuredModelFailure.RetryAfterDisposition.VALID))
                .thenReturn(oneSecond.output);

        oneSecond.executor.execute(
                oneSecond.binding, oneSecond.request, oneSecond.compiler);

        assertThat(oneSecond.sleeps).containsExactly(1_000L);
        assertThat(oneSecond.events).singleElement().satisfies(event ->
                assertThat(event.getFields().get("wait.bucket"))
                        .isEqualTo("RETRY_AFTER_LE_1S"));
        verify(oneSecond.gateway, times(2)).execute(
                same(oneSecond.binding), same(oneSecond.request),
                same(oneSecond.compiler));

        assertNoRetry(StructuredModelFailure.rateLimited(
                429, 2,
                StructuredModelFailure.RetryAfterDisposition.VALID));
        assertNoRetry(StructuredModelFailure.rateLimited(
                429, null,
                StructuredModelFailure.RetryAfterDisposition.INVALID));
    }

    @Test
    void nonApprovedHttpAndContentFailuresAreNeverRetried() {
        assertNoRetry(new StructuredModelFailure(
                StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE));
        assertNoRetry(StructuredModelFailure.deadline(
                StructuredModelFailure.TimeoutDisposition.RESPONSE_STARTED,
                null));
        assertNoRetry(StructuredModelFailure.deadline(
                StructuredModelFailure.TimeoutDisposition.UNKNOWN,
                null));
        assertNoRetry(StructuredModelFailure.cancelled(
                new java.util.concurrent.CancellationException("test cancel")));
        assertNoRetry(StructuredModelFailure.http(
                StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 500));
        assertNoRetry(StructuredModelFailure.http(
                StructuredModelFailure.Code.PROVIDER_REJECTED, 400));
        assertNoRetry(StructuredModelFailure.http(
                StructuredModelFailure.Code.AUTHENTICATION_REJECTED, 401));
        assertNoRetry(StructuredModelFailure.http(
                StructuredModelFailure.Code.AUTHENTICATION_REJECTED, 403));
        assertNoRetry(new StructuredModelFailure(
                StructuredModelFailure.Code.RESPONSE_TOO_LARGE));
        assertNoRetry(new StructuredModelFailure(
                StructuredModelFailure.Code.RESPONSE_JSON_INVALID));
        assertNoRetry(new StructuredModelFailure(
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID));
        assertNoRetry(new StructuredModelFailure(
                StructuredModelFailure.Code.INVALID_RESPONSE));

        Fixture fixture = fixture(Duration.ofSeconds(10), 175);
        StructuredOutputValidationException contentFailure =
                new StructuredOutputValidationException(
                        StructuredOutputValidationException.Reason
                                .MISSING_REQUIRED_FIELD);
        when(fixture.gateway.execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler))).thenThrow(contentFailure);

        assertThatThrownBy(() -> fixture.executor.execute(
                fixture.binding, fixture.request, fixture.compiler))
                .isSameAs(contentFailure);
        verify(fixture.gateway).execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler));
    }

    @Test
    void remainingDeadlineMustCoverWaitAndThreeSecondRetryFloor() {
        Fixture below = fixture(Duration.ofMillis(3_999), 175);
        when(below.gateway.execute(
                same(below.binding), same(below.request), same(below.compiler)))
                .thenThrow(StructuredModelFailure.rateLimited(
                        429, 1,
                        StructuredModelFailure.RetryAfterDisposition.VALID));

        assertThatThrownBy(() -> below.executor.execute(
                below.binding, below.request, below.compiler))
                .isInstanceOf(StructuredModelFailure.class);
        assertThat(below.sleeps).isEmpty();
        verify(below.gateway).execute(
                same(below.binding), same(below.request), same(below.compiler));

        Fixture exact = fixture(Duration.ofMillis(4_000), 175);
        when(exact.gateway.execute(
                same(exact.binding), same(exact.request), same(exact.compiler)))
                .thenThrow(StructuredModelFailure.rateLimited(
                        429, 1,
                        StructuredModelFailure.RetryAfterDisposition.VALID))
                .thenReturn(exact.output);

        exact.executor.execute(exact.binding, exact.request, exact.compiler);

        assertThat(exact.sleeps).containsExactly(1_000L);
        assertThat(exact.request.deadline().remainingMillis()).isEqualTo(3_000L);
        verify(exact.gateway, times(2)).execute(
                same(exact.binding), same(exact.request), same(exact.compiler));

        Fixture noWaitBelow = fixture(Duration.ofMillis(2_999), 175);
        when(noWaitBelow.gateway.execute(
                same(noWaitBelow.binding), same(noWaitBelow.request),
                same(noWaitBelow.compiler)))
                .thenThrow(StructuredModelFailure.deadline(
                        StructuredModelFailure.TimeoutDisposition.NO_RESPONSE,
                        null));

        assertThatThrownBy(() -> noWaitBelow.executor.execute(
                noWaitBelow.binding, noWaitBelow.request,
                noWaitBelow.compiler))
                .isInstanceOf(StructuredModelFailure.class);
        verify(noWaitBelow.gateway).execute(
                same(noWaitBelow.binding), same(noWaitBelow.request),
                same(noWaitBelow.compiler));

        Fixture noWaitExact = fixture(Duration.ofMillis(3_000), 175);
        when(noWaitExact.gateway.execute(
                same(noWaitExact.binding), same(noWaitExact.request),
                same(noWaitExact.compiler)))
                .thenThrow(StructuredModelFailure.deadline(
                        StructuredModelFailure.TimeoutDisposition.NO_RESPONSE,
                        null))
                .thenReturn(noWaitExact.output);

        noWaitExact.executor.execute(
                noWaitExact.binding, noWaitExact.request,
                noWaitExact.compiler);
        verify(noWaitExact.gateway, times(2)).execute(
                same(noWaitExact.binding), same(noWaitExact.request),
                same(noWaitExact.compiler));
    }

    @Test
    void missingRetryAfterRejectsOutOfRangeJitterWithoutASecondCall() {
        for (int jitter : List.of(99, 251)) {
            Fixture fixture = fixture(Duration.ofSeconds(10), jitter);
            when(fixture.gateway.execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler)))
                    .thenThrow(StructuredModelFailure.rateLimited(
                            429, null,
                            StructuredModelFailure.RetryAfterDisposition.MISSING));

            assertThatThrownBy(() -> fixture.executor.execute(
                    fixture.binding, fixture.request, fixture.compiler))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                            "retry jitter must be between 100 and 250 milliseconds");
            verify(fixture.gateway).execute(
                    same(fixture.binding), same(fixture.request),
                    same(fixture.compiler));
        }
    }

    @Test
    void retryFailureStopsAtTwoAttemptsAndPropagatesTheSecondFailure() {
        Fixture fixture = fixture(Duration.ofSeconds(10), 175);
        StructuredModelFailure first = StructuredModelFailure.http(
                StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 503);
        StructuredModelFailure second = StructuredModelFailure.http(
                StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 504);
        when(fixture.gateway.execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler))).thenThrow(first).thenThrow(second);

        assertThatThrownBy(() -> fixture.executor.execute(
                fixture.binding, fixture.request, fixture.compiler))
                .isSameAs(second);
        verify(fixture.gateway, times(2)).execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler));
    }

    @Test
    void interruptedWaitRestoresInterruptAndNeverStartsSecondAttempt() {
        Fixture fixture = fixture(
                Duration.ofSeconds(10), 175,
                millis -> { throw new InterruptedException("test interrupt"); });
        when(fixture.gateway.execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler)))
                .thenThrow(StructuredModelFailure.rateLimited(
                        429, null,
                        StructuredModelFailure.RetryAfterDisposition.MISSING));

        StructuredModelFailure failure = catchThrowableOfType(
                () -> fixture.executor.execute(
                        fixture.binding, fixture.request, fixture.compiler),
                StructuredModelFailure.class);

        assertThat(failure.getCode()).isEqualTo(
                StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
        verify(fixture.gateway).execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler));
    }

    @Test
    void duplicateAttemptIdentityFailsClosedBeforeASecondProviderCall() {
        StructuredOutputGateway gateway = mock(StructuredOutputGateway.class);
        ModelTransportBinding binding = mock(ModelTransportBinding.class);
        MutableClock clock = new MutableClock();
        StructuredModelRequest request = request(clock, Duration.ofSeconds(10));
        StructuredOutputCompiler compiler = compiler();
        UUID duplicate = UUID.randomUUID();
        GeneralTransportRetryExecutor executor =
                new GeneralTransportRetryExecutor(
                        gateway, millis -> clock.advance(millis),
                        () -> 175, () -> duplicate);
        forwardAttemptAwareCalls(gateway);
        when(gateway.execute(same(binding), same(request), same(compiler)))
                .thenThrow(StructuredModelFailure.http(
                        StructuredModelFailure.Code.PROVIDER_UNAVAILABLE, 503));

        assertThatThrownBy(() -> executor.execute(binding, request, compiler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider attempt identity must be unique");
        verify(gateway).execute(same(binding), same(request), same(compiler));
        verify(gateway).execute(
                same(binding), same(request), same(compiler),
                any(ProviderAttemptContext.class));
    }

    @Test
    void scheduledRetryPublishesOnlyTheApprovedLowCardinalityFields() {
        Fixture fixture = fixture(Duration.ofSeconds(10), 175);
        when(fixture.gateway.execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler)))
                .thenThrow(StructuredModelFailure.rateLimited(
                        429, null,
                        StructuredModelFailure.RetryAfterDisposition.MISSING))
                .thenReturn(fixture.output);

        fixture.executor.execute(
                fixture.binding, fixture.request, fixture.compiler);

        assertThat(fixture.events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo(
                    "provider.call.retry_scheduled");
            assertThat(event.getFields()).containsOnly(
                    org.assertj.core.data.MapEntry.entry("attempt.index", 2),
                    org.assertj.core.data.MapEntry.entry("attempt.count", 2),
                    org.assertj.core.data.MapEntry.entry(
                            "failure.code", "RATE_LIMITED"),
                    org.assertj.core.data.MapEntry.entry(
                            "wait.bucket", "JITTER_100_250_MS"));
            assertThat(event.toString()).doesNotContain(
                    "providerAttemptId", "authorization", "body");
        });
    }

    @Test
    void rejectedRetryDoesNotPublishAScheduledEvent() {
        Fixture fixture = fixture(Duration.ofMillis(2_999), 175);
        when(fixture.gateway.execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler)))
                .thenThrow(StructuredModelFailure.deadline(
                        StructuredModelFailure.TimeoutDisposition.NO_RESPONSE,
                        null));

        assertThatThrownBy(() -> fixture.executor.execute(
                fixture.binding, fixture.request, fixture.compiler))
                .isInstanceOf(StructuredModelFailure.class);

        assertThat(fixture.events).isEmpty();
    }

    private void assertNoRetry(StructuredModelFailure failure) {
        Fixture fixture = fixture(Duration.ofSeconds(10), 175);
        when(fixture.gateway.execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler))).thenThrow(failure);

        assertThatThrownBy(() -> fixture.executor.execute(
                fixture.binding, fixture.request, fixture.compiler))
                .isSameAs(failure);
        verify(fixture.gateway).execute(
                same(fixture.binding), same(fixture.request),
                same(fixture.compiler));
        assertThat(fixture.sleeps).isEmpty();
    }

    private Fixture fixture(Duration remaining, int jitter) {
        MutableClock clock = new MutableClock();
        return fixture(remaining, jitter, millis -> clock.advance(millis), clock);
    }

    private Fixture fixture(
            Duration remaining, int jitter,
            GeneralTransportRetryExecutor.Sleeper sleeper) {
        return fixture(remaining, jitter, sleeper, new MutableClock());
    }

    private Fixture fixture(
            Duration remaining, int jitter,
            GeneralTransportRetryExecutor.Sleeper sleeper,
            MutableClock clock) {
        StructuredOutputGateway gateway = mock(StructuredOutputGateway.class);
        forwardAttemptAwareCalls(gateway);
        ModelTransportBinding binding = mock(ModelTransportBinding.class);
        StructuredModelRequest request = request(clock, remaining);
        StructuredOutputCompiler compiler = compiler();
        StructurallyValidatedOutput output = StructuredModelTestFixtures.validatedGeneral("""
                {"topic":"并发控制","statements":[
                  {"role":"DEFINITION","text":"定义。","subject":null,
                   "dimension":null,"aspects":["DEFINITION"]},
                  {"role":"MECHANISM","text":"机制。","subject":null,
                   "dimension":null,"aspects":["MECHANISM"]}],"caveats":[]}
                """);
        List<Long> sleeps = new ArrayList<>();
        List<UUID> attemptIds = new ArrayList<>();
        List<DiagnosticEvent> events = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger();
        GeneralTransportRetryExecutor executor =
                new GeneralTransportRetryExecutor(
                        gateway,
                        new ModelOutputDiagnostics(events::add),
                        millis -> {
                            sleeps.add(millis);
                            sleeper.sleep(millis);
                        },
                        () -> jitter,
                        () -> {
                            UUID value = new UUID(
                                    0L, sequence.incrementAndGet());
                            attemptIds.add(value);
                            return value;
                        });
        return new Fixture(
                gateway, binding, request, compiler, output,
                executor, sleeps, attemptIds, events);
    }

    private StructuredModelRequest request(
            MutableClock clock, Duration remaining) {
        return new StructuredModelRequest(
                ModelOperation.GENERAL_KNOWLEDGE,
                "system", "user", 32, 0.0d,
                TurnDeadline.after(remaining, clock));
    }

    private void forwardAttemptAwareCalls(StructuredOutputGateway gateway) {
        when(gateway.execute(
                any(ModelTransportBinding.class),
                any(StructuredModelRequest.class),
                any(StructuredOutputCompiler.class),
                any(ProviderAttemptContext.class)))
                .thenAnswer(invocation -> gateway.execute(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));
    }

    private StructuredOutputCompiler compiler() {
        return StructuredOutputCompiler.named(
                OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION,
                node -> node);
    }

    private record Fixture(
            StructuredOutputGateway gateway,
            ModelTransportBinding binding,
            StructuredModelRequest request,
            StructuredOutputCompiler compiler,
            StructurallyValidatedOutput output,
            GeneralTransportRetryExecutor executor,
            List<Long> sleeps,
            List<UUID> attemptIds,
            List<DiagnosticEvent> events) { }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-28T00:00:00Z");

        void advance(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() { return instant; }
    }

}
