package com.portfolio.agent.answer.controller;

import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.response.ConversationAnswerResponse;
import com.portfolio.agent.answer.mapper.ConversationAnswerResponseMapper;
import com.portfolio.agent.answer.service.ProductionConversationService;
import com.portfolio.agent.common.web.ClientAddressResolver;
import com.portfolio.agent.common.web.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/answers")
public final class ConversationAnswerController {

    private final ProductionConversationService service;
    private final ClientAddressResolver clientAddressResolver;
    private final ConversationAnswerResponseMapper responseMapper;

    public ConversationAnswerController(
            ProductionConversationService service,
            ClientAddressResolver clientAddressResolver,
            ConversationAnswerResponseMapper responseMapper
    ) {
        this.service = service;
        this.clientAddressResolver = clientAddressResolver;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public ConversationAnswerResponse answer(
            @Valid @RequestBody ConversationAnswerRequest request,
            HttpServletRequest servletRequest
    ) {
        RequestContextHolder.enrichTurnId(request.getTurnId());
        ConversationAnswerResult result = service.answer(
                request, clientAddressResolver.resolve(servletRequest));
        return responseMapper.toResponse(result);
    }
}
