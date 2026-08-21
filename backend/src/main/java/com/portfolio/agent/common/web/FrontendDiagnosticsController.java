package com.portfolio.agent.common.web;

import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.FrontendDiagnosticAdmissionGate;
import com.portfolio.agent.common.observability.FrontendDiagnosticProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/client-diagnostics")
public final class FrontendDiagnosticsController {

    private final FrontendDiagnosticProperties properties;
    private final FrontendDiagnosticAdmissionGate admissionGate;
    private final ClientAddressResolver clientAddressResolver;
    private final AnonymousSourceHasher sourceHasher;
    private final DiagnosticEventPublisher publisher;

    public FrontendDiagnosticsController(
            FrontendDiagnosticProperties properties,
            FrontendDiagnosticAdmissionGate admissionGate,
            ClientAddressResolver clientAddressResolver,
            AnonymousSourceHasher sourceHasher,
            DiagnosticEventPublisher publisher
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.admissionGate = Objects.requireNonNull(admissionGate, "admissionGate must not be null");
        this.clientAddressResolver = Objects.requireNonNull(
                clientAddressResolver, "clientAddressResolver must not be null");
        this.sourceHasher = Objects.requireNonNull(sourceHasher, "sourceHasher must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    @PostMapping
    public ResponseEntity<Void> ingest(
            @Valid @RequestBody FrontendDiagnosticBatchRequest batch,
            HttpServletRequest servletRequest
    ) {
        if (!properties.isFrontendIngestEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (batch.getEvents().size() > properties.getFrontendMaxBatchSize()) {
            return ResponseEntity.badRequest().build();
        }
        String sourceAddress = clientAddressResolver.resolve(servletRequest);
        String sourceHash = sourceHasher.hash(sourceAddress);
        if (!admissionGate.tryAdmit(sourceHash, batch.getEvents().size())) {
            return ResponseEntity.status(429).build();
        }
        for (FrontendDiagnosticEventRequest request : batch.getEvents()) {
            publishSafely(toDiagnosticEvent(request));
        }
        return ResponseEntity.accepted().build();
    }

    private DiagnosticEvent toDiagnosticEvent(FrontendDiagnosticEventRequest request) {
        DiagnosticEvent.Builder builder = DiagnosticEvent.builder(
                        request.getEventName().getValue(),
                        request.getEventName().getLevel())
                .field("event.origin", "browser")
                .field("client.session.id", request.getClientSessionId())
                .field("client.request.id", request.getClientRequestId());
        addOptionalField(
                builder, "client.reported_request_id", request.getServerRequestId());
        addOptionalField(builder, "turn.id", request.getTurnId());
        addOptionalField(builder, "error.code", request.getErrorCode());
        addOptionalEnumField(builder, "error.kind", request.getErrorKind());
        addOptionalField(builder, "error.fingerprint", request.getErrorFingerprint());
        addOptionalEnumField(builder, "duration.bucket", request.getDurationBucket());
        addOptionalNumberField(builder, "http.status_code", request.getHttpStatus());
        addOptionalEnumField(builder, "generation.mode", request.getGenerationMode());
        addOptionalEnumField(builder, "guidance.stage", request.getGuidanceStage());
        addOptionalNumberField(
                builder, "suggestion.count", request.getSuggestedQuestionCount());
        addOptionalField(builder, "content.version", request.getContentVersion());
        addOptionalNumberField(builder, "recovery.count", request.getRecoveredCount());
        return builder.build();
    }

    private void addOptionalField(
            DiagnosticEvent.Builder builder,
            String key,
            String value
    ) {
        if (value != null) {
            builder.field(key, value);
        }
    }

    private void addOptionalEnumField(
            DiagnosticEvent.Builder builder,
            String key,
            Enum<?> value
    ) {
        if (value != null) {
            builder.field(key, value);
        }
    }

    private void addOptionalNumberField(
            DiagnosticEvent.Builder builder,
            String key,
            Number value
    ) {
        if (value != null) {
            builder.field(key, value);
        }
    }

    private void addOptionalBooleanField(
            DiagnosticEvent.Builder builder,
            String key,
            Boolean value
    ) {
        if (value != null) {
            builder.field(key, value);
        }
    }

    private void publishSafely(DiagnosticEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException diagnosticsFailure) {
            // Frontend diagnostics remain best effort and never alter the HTTP response.
        }
    }
}
