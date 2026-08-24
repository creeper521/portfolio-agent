package com.portfolio.agent.turn.api;

import com.portfolio.agent.turn.api.request.AgentTurnRequest;
import com.portfolio.agent.turn.api.request.AgentTurnRequestMapper;
import com.portfolio.agent.turn.api.response.AgentApiErrorResponse;
import com.portfolio.agent.turn.api.response.PublicAgentTurnResponse;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.turn.lifecycle.ActiveTurnCapacity;
import com.portfolio.agent.turn.lifecycle.AgentAdmissionRejectedException;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent/turns")
public final class AgentTurnController {
    private final AgentTurnLifecycleService lifecycle;
    private final AgentTurnRequestMapper mapper;
    private final ClientAddressResolver clientAddressResolver;
    private final AnonymousSourceHasher sourceHasher;
    private final AgentRequestAdmissionGate admissionGate;
    private final ActiveTurnCapacity activeTurnCapacity;

    public AgentTurnController(
            AgentTurnLifecycleService lifecycle,
            AgentTurnRequestMapper mapper,
            ClientAddressResolver clientAddressResolver,
            AnonymousSourceHasher sourceHasher,
            AgentRequestAdmissionGate admissionGate,
            ActiveTurnCapacity activeTurnCapacity) {
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.clientAddressResolver = clientAddressResolver;
        this.sourceHasher = sourceHasher;
        this.admissionGate = admissionGate;
        this.activeTurnCapacity = activeTurnCapacity;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody AgentTurnRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest httpRequest) {
        Bearer bearer = bearer(authorization, false);
        if (!bearer.valid()) return error(
                HttpStatus.UNAUTHORIZED, request.getRequestId(), "RESUME_TOKEN_INVALID",
                "会话凭证无效或已过期。", false, null);
        String sourceHash = sourceHasher.hash(clientAddressResolver.resolve(httpRequest));
        try (AgentRequestAdmission ignoredSource = admissionGate.acquire(
                sourceHash, request.getRequestId());
             ActiveTurnCapacity.Lease ignoredActive = activeTurnCapacity.acquire()) {
            AgentTurnLifecycleService.Result result = lifecycle.execute(
                    bearer.token(), mapper.toCommand(request));
            if (result.settlementFailed()) {
                return error(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        request.getRequestId(),
                        "AGENT_STATE_UNAVAILABLE",
                        "Agent 状态服务暂时不可用。",
                        true,
                        3L);
            }
            return switch (result.status()) {
                case COMPLETED, REPLAY -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(new PublicAgentTurnResponse(result.turn(), result.conversation()));
                case IN_PROGRESS -> error(
                        HttpStatus.CONFLICT, request.getRequestId(), "TURN_IN_PROGRESS",
                        "相同请求仍在处理中。", true, result.retryAfterSeconds());
                case CONFLICT -> error(
                        HttpStatus.CONFLICT, request.getRequestId(), "IDEMPOTENCY_KEY_CONFLICT",
                        "同一 requestId 不能用于不同请求。", false, null);
                case CANCELLED -> error(
                        HttpStatus.CONFLICT, request.getRequestId(), "TURN_CANCELLED",
                        "该请求已取消。", false, null);
                case STORE_UNAVAILABLE -> error(
                        HttpStatus.SERVICE_UNAVAILABLE, request.getRequestId(), "AGENT_STATE_UNAVAILABLE",
                        "Agent 状态服务暂时不可用。", true, 3L);
                case UNAUTHORIZED -> error(
                        HttpStatus.UNAUTHORIZED, request.getRequestId(), "RESUME_TOKEN_INVALID",
                        "会话凭证无效或已过期。", false, null);
            };
        } catch (AgentAdmissionRejectedException rejection) {
            long retryAfterSeconds = rejection.getRetryAfterSeconds();
            return error(
                    HttpStatus.TOO_MANY_REQUESTS,
                    request.getRequestId(),
                    "RATE_LIMITED",
                    "请求过于频繁，请稍后再试。",
                    true,
                    retryAfterSeconds);
        }
    }

    @DeleteMapping("/{requestId}")
    public ResponseEntity<?> cancel(
            @PathVariable UUID requestId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Bearer bearer = bearer(authorization, false);
        if (!bearer.valid()) return error(
                HttpStatus.UNAUTHORIZED, requestId, "RESUME_TOKEN_INVALID",
                "会话凭证无效或已过期。", false, null);
        return switch (lifecycle.cancel(bearer.token(), requestId)) {
            case CANCELLED -> ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
            case ALREADY_COMPLETED -> error(
                    HttpStatus.CONFLICT, requestId, "TURN_ALREADY_COMPLETED",
                    "该请求已经完成。", false, null);
            case NOT_FOUND -> error(
                    HttpStatus.NOT_FOUND, requestId, "TURN_NOT_FOUND",
                    "未找到可取消的请求。", false, null);
            case UNAUTHORIZED -> error(
                    HttpStatus.UNAUTHORIZED, requestId, "RESUME_TOKEN_INVALID",
                    "会话凭证无效或已过期。", false, null);
            case STORE_UNAVAILABLE -> error(
                    HttpStatus.SERVICE_UNAVAILABLE, requestId, "AGENT_STATE_UNAVAILABLE",
                    "Agent 状态服务暂时不可用。", true, 3L);
        };
    }

    private ResponseEntity<AgentApiErrorResponse> error(
            HttpStatus status, UUID requestId, String code, String message,
            boolean retryable, Long retryAfter) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (retryAfter != null) builder.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        return builder.body(AgentApiErrorResponse.of(
                requestId, code, message, retryable, retryAfter));
    }

    static Bearer bearer(String authorization, boolean required) {
        if (authorization == null || authorization.isBlank()) {
            return required ? new Bearer(false, null) : new Bearer(true, null);
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            return new Bearer(false, null);
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() || token.contains(" ") ? new Bearer(false, null) : new Bearer(true, token);
    }
    record Bearer(boolean valid, String token) { }
}
