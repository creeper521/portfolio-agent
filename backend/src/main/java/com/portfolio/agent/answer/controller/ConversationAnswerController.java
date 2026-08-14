package com.portfolio.agent.answer.controller;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.dto.response.CompletionReceiptResponse;
import com.portfolio.agent.answer.dto.response.ConversationResponse;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.service.ProductionConversationService;
import com.portfolio.agent.answer.service.ProductionConversationExecution;
import com.portfolio.agent.answer.service.RequestReceiptConflictException;
import com.portfolio.agent.answer.service.RequestReceiptInProgressException;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.service.ConversationRequestContext;
import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.time.Instant;

@RestController
@RequestMapping("/api/v2/answers")
public final class ConversationAnswerController {

    private final ProductionConversationService service;
    private final ClientAddressResolver clientAddressResolver;
    private final ConversationAnswerResponseMapper responseMapper;
    private final Optional<ConversationBusinessContextStore> contextStore;

    public ConversationAnswerController(
            ProductionConversationService service,
            ClientAddressResolver clientAddressResolver,
            ConversationAnswerResponseMapper responseMapper
    ) {
        this(service, clientAddressResolver, responseMapper, Optional.empty());
    }

    @Autowired
    public ConversationAnswerController(
            ProductionConversationService service,
            ClientAddressResolver clientAddressResolver,
            ConversationAnswerResponseMapper responseMapper,
            Optional<ConversationBusinessContextStore> contextStore
    ) {
        this.service = service;
        this.clientAddressResolver = clientAddressResolver;
        this.responseMapper = responseMapper;
        this.contextStore = contextStore;
    }

    @PostMapping
    public Object answer(
            @Valid @RequestBody ConversationAnswerRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        servletResponse.setHeader("Cache-Control", "no-store");
        return answerHttpInternal(request, servletRequest);
    }

    /** Source-compatible direct-call adapter for existing unit tests and non-HTTP callers. */
    public ConversationAnswerResponse answer(
            ConversationAnswerRequest request,
            HttpServletRequest servletRequest
    ) {
        return answerInternal(request, servletRequest);
    }

    private ConversationAnswerResponse answerInternal(
            ConversationAnswerRequest request, HttpServletRequest servletRequest) {
        RequestContextHolder.enrichTurnId(request.getTurnId());
        String encodedToken = servletRequest.getHeader("X-Conversation-Resume-Token");
        ConversationRequestContext requestContext = requestContext(encodedToken, request);
        ConversationAnswerResult result = requestContext == null
                ? service.answer(request, clientAddressResolver.resolve(servletRequest))
                : service.answer(request, clientAddressResolver.resolve(servletRequest), requestContext);
        return responseMapper.toResponse(result, conversationResponse(requestContext));
    }

    private Object answerHttpInternal(
            ConversationAnswerRequest request, HttpServletRequest servletRequest) {
        RequestContextHolder.enrichTurnId(request.getTurnId());
        String encodedToken = servletRequest.getHeader("X-Conversation-Resume-Token");
        if (encodedToken == null || encodedToken.isBlank()) {
            Optional<com.portfolio.agent.answer.context.domain.CompletionReceipt> completed =
                    service.findCompleted(request);
            if (completed.isPresent() && contextStore.isPresent()) {
                com.portfolio.agent.answer.context.domain.CompletionReceipt receipt = completed.orElseThrow();
                ResumeToken replacement = ResumeToken.issue();
                contextStore.orElseThrow().rotateResumeToken(
                        receipt.getConversationId(), replacement, Instant.now());
                ConversationRequestContext recoveredContext = new ConversationRequestContext(
                        receipt.getConversationId(), replacement, null, true);
                return new CompletionReceiptResponse(
                        request.getTurnId(), receipt, conversationResponse(recoveredContext));
            }
        }
        ConversationRequestContext requestContext = requestContext(encodedToken, request);
        if (requestContext == null) {
            try {
                ConversationAnswerResult result = service.answer(
                        request, clientAddressResolver.resolve(servletRequest));
                return responseMapper.toResponse(result, null);
            } catch (RequestReceiptConflictException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", exception);
            }
        }
        final ProductionConversationExecution execution;
        try {
            execution = service.execute(
                    request, clientAddressResolver.resolve(servletRequest), requestContext);
        } catch (RequestReceiptInProgressException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", exception);
        } catch (RequestReceiptConflictException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", exception);
        }
        ConversationResponse conversation = conversationResponse(
                requestContext, execution.getContinuationStatus());
        if (execution.getCompletionReceipt().isPresent()) {
            return new CompletionReceiptResponse(
                    request.getTurnId(), execution.getCompletionReceipt().orElseThrow(), conversation);
        }
        return responseMapper.toResponse(
                execution.getAnswer().orElseThrow(), conversation, execution.getContextHandles());
    }

    private ConversationResponse conversationResponse(ConversationRequestContext requestContext) {
        return conversationResponse(requestContext, null);
    }

    private ConversationResponse conversationResponse(
            ConversationRequestContext requestContext,
            com.portfolio.agent.answer.context.domain.ConversationContinuationStatus executionStatus) {
        if (contextStore.isEmpty()) {
            return null;
        }
        if (requestContext == null) {
            return new ConversationResponse(null,
                    com.portfolio.agent.answer.context.domain.ConversationContinuationStatus.NOT_APPLICABLE, null);
        }
        boolean present = contextStore.orElseThrow()
                .findConversation(requestContext.getResumeToken()).isPresent();
        com.portfolio.agent.answer.context.domain.ConversationContinuationStatus effectiveStatus =
                executionStatus == null
                        ? present
                        ? com.portfolio.agent.answer.context.domain.ConversationContinuationStatus.AVAILABLE
                        : com.portfolio.agent.answer.context.domain.ConversationContinuationStatus.CONTEXT_EXPIRED
                        : executionStatus;
        if (requestContext.isNewConversation()) {
            String resumeToken = effectiveStatus
                    == com.portfolio.agent.answer.context.domain.ConversationContinuationStatus.AVAILABLE
                    ? requestContext.getResumeToken().asBase64Url() : null;
            return new ConversationResponse(
                    resumeToken,
                    effectiveStatus,
                    null);
        }
        return new ConversationResponse(
                null,
                effectiveStatus,
                null);
    }

    private ConversationRequestContext requestContext(
            String encodedToken, ConversationAnswerRequest request) {
        if (contextStore.isEmpty()) {
            return null;
        }
        if (encodedToken == null || encodedToken.isBlank()) {
            ConversationId conversationId = ConversationId.random();
            ResumeToken token = ResumeToken.issue();
            contextStore.orElseThrow().open(conversationId, token, Instant.now());
            return new ConversationRequestContext(conversationId, token, null, true);
        }
        final ResumeToken token;
        try {
            token = ResumeToken.fromBase64Url(encodedToken);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_CONVERSATION_RESUME_TOKEN");
        }
        Optional<ConversationId> conversationId = contextStore.orElseThrow().findConversation(token);
        if (conversationId.isEmpty()) {
            return new ConversationRequestContext(ConversationId.random(), token, null, false);
        }
        AuthorizedContextReference reference = request.getContextReference() == null ? null
                : new AuthorizedContextReference(
                        request.getContextReference().getContextHandle(),
                        request.getContextReference().getExpectedContextType().name(),
                        null,
                        request.getContextReference().getResultItemId());
        return new ConversationRequestContext(conversationId.orElseThrow(), token, reference, false);
    }
}
