package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.PublicContentResponse;
import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.infrastructure.model.policy.ConversationProviderAccess;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public-content")
public class PublicContentController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;
    private final AgentAvailabilityResponse agentAvailability;

    public PublicContentController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper,
            @Value("${portfolio.conversation-context.mode:DISABLED}") String contextMode,
            @Value("${portfolio.model-operations.turn-interpretation.mode:DISABLED}")
            String turnInterpretationMode,
            ConversationProviderAccess providerAccess
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
        this.agentAvailability = "DISABLED".equalsIgnoreCase(contextMode)
                ? AgentAvailabilityResponse.unavailable()
                : AgentAvailabilityResponse.available(
                        "ENABLED".equalsIgnoreCase(turnInterpretationMode)
                                && providerAccess.isAllowed()
                                ? AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE
                                : AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
    }

    @GetMapping
    public ResponseEntity<PublicContentResponse> getPublicContent() {
        PublicContentResponse response = responseMapper.toPublicContentResponse(
                portfolioService.getPublicContent(), agentAvailability);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
