package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioSnapshotResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.turn.infrastructure.AgentRuntimeReadiness;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public final class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;
    private final AgentAvailabilityResponse agentAvailability;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper,
            AgentRuntimeReadiness readiness,
            ModelCatalogSnapshot modelCatalog
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
        this.agentAvailability = !readiness.isAgentAvailable()
                ? AgentAvailabilityResponse.unavailable(modelCatalog)
                : AgentAvailabilityResponse.available(
                        readiness.isOperationAvailable(ModelOperation.TURN_INTERPRETATION)
                                ? AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE
                                : AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED,
                        modelCatalog);
    }

    @GetMapping
    public ResponseEntity<PortfolioSnapshotResponse> getPortfolioSnapshot() {
        PortfolioSnapshotResponse response = responseMapper.toPortfolioSnapshotResponse(
                portfolioService.getPublicContent(), agentAvailability);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
