package com.portfolio.agent.turn.api;

import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestContextHolder;
import com.portfolio.agent.turn.api.request.AgentTurnRequest;
import com.portfolio.agent.turn.api.request.AgentTurnRequestMapper;
import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.MigrationProductionTurnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Slice-1 HTTP replacement; registered atomically when the old controller is removed. */
@RestController
@RequestMapping("/api/v2/answers")
public final class MigrationConversationAnswerController {
    private final MigrationProductionTurnService service;
    private final AgentTurnRequestMapper requestMapper;
    private final ConversationAnswerResponseMapper responseMapper;
    private final ClientAddressResolver clientAddressResolver;

    public MigrationConversationAnswerController(
            MigrationProductionTurnService service,
            AgentTurnRequestMapper requestMapper,
            ConversationAnswerResponseMapper responseMapper,
            ClientAddressResolver clientAddressResolver) {
        this.service = Objects.requireNonNull(service, "service");
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper");
        this.clientAddressResolver = Objects.requireNonNull(clientAddressResolver, "clientAddressResolver");
    }

    @PostMapping
    public ConversationAnswerResponse answer(
            @Valid @RequestBody AgentTurnRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        servletResponse.setHeader("Cache-Control", "no-store");
        RequestContextHolder.enrichTurnId(request.getRequestId().toString());
        AgentTurnCommand command = requestMapper.toCommand(request);
        try {
            return responseMapper.toResponse(
                    service.answer(command, clientAddressResolver.resolve(servletRequest)), null);
        } catch (com.portfolio.agent.answer.service.RequestReceiptConflictException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", conflict);
        }
    }
}
