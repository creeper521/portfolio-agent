package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentConfiguration;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationDraftValidationResult;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;

import java.util.List;

public final class ConversationalAgentRuntime {

    private final PortfolioKnowledgeGateway knowledgeGateway;
    private final ConversationWindowManager windowManager;
    private final ConversationIntentRouter intentRouter;
    private final PortfolioGroundingAssembler groundingAssembler;
    private final ConversationToolService toolService;
    private final ConversationalModelPort modelPort;
    private final ConversationDraftValidator draftValidator;
    private final DynamicQuestionService questionService;
    private final DeterministicConversationFallback fallback;
    private final ConversationalAgentConfiguration.ConversationProviderAccess providerAccess;

    public ConversationalAgentRuntime(
            PortfolioKnowledgeGateway knowledgeGateway,
            ConversationWindowManager windowManager,
            ConversationIntentRouter intentRouter,
            PortfolioGroundingAssembler groundingAssembler,
            ConversationToolService toolService,
            ConversationalModelPort modelPort,
            ConversationDraftValidator draftValidator,
            DynamicQuestionService questionService,
            DeterministicConversationFallback fallback,
            ConversationalAgentConfiguration.ConversationProviderAccess providerAccess
    ) {
        this.knowledgeGateway = knowledgeGateway;
        this.windowManager = windowManager;
        this.intentRouter = intentRouter;
        this.groundingAssembler = groundingAssembler;
        this.toolService = toolService;
        this.modelPort = modelPort;
        this.draftValidator = draftValidator;
        this.questionService = questionService;
        this.fallback = fallback;
        this.providerAccess = providerAccess;
    }

    public ConversationAnswerResult answer(ConversationAnswerRequest request) {
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        if (!providerAccess.isAllowed()) {
            return fallback.answer(request, content);
        }
        ConversationWindow window = windowManager.prepare(
                request.getMessages(), request.getQuestion());
        ConversationRoute route = intentRouter.route(content, window, request);
        if (route.getIntent()
                == com.portfolio.agent.answer.domain.ConversationIntent.TIME_SENSITIVE
                || route.getIntent()
                == com.portfolio.agent.answer.domain.ConversationIntent.UNSUPPORTED_OR_UNSAFE) {
            return fallback.answer(request, content, route);
        }
        PortfolioGroundingContext grounding = groundingAssembler.assemble(
                content, route, request.getQuestion());
        grounding = toolService.enrich(
                content, request.getQuestion(), window, route, grounding);
        ConversationModelResult<ConversationDraft> generated = modelPort.generate(
                request.getQuestion(), window, route, grounding);
        if (generated == null || !generated.isSuccessful()) {
            return fallback.answer(request, content, route);
        }
        ConversationDraftValidationResult validated = draftValidator.validate(
                generated.getValue(), route.getAnswerScope(), grounding);
        if (!validated.isValid()) {
            return fallback.answer(request, content, route);
        }
        List<ConversationSuggestedQuestion> suggestions = questionService.generate(
                content, route, window, validated.getAcceptedBlocks());
        return new ConversationAnswerResult(
                request.getTurnId(),
                content.getContentVersion(),
                route.getIntent(),
                route.getAnswerScope(),
                validated.getResolution(),
                validated.getTitle(),
                validated.getAcceptedBlocks(),
                suggestions,
                false);
    }
}
