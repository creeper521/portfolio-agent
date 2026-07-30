package com.portfolio.agent.selection.controller;

import com.portfolio.agent.selection.domain.PortfolioSelectionResult;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.dto.PortfolioSelectionRequest;
import com.portfolio.agent.selection.dto.PortfolioSelectionResponse;
import com.portfolio.agent.selection.mapper.PortfolioSelectionResponseMapper;
import com.portfolio.agent.selection.service.PortfolioSelectionService;
import java.util.Objects;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio-selections")
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "true")
public class PortfolioSelectionController {

    private final PortfolioSelectionService service;
    private final PortfolioSelectionResponseMapper mapper;

    public PortfolioSelectionController(
            PortfolioSelectionService service,
            PortfolioSelectionResponseMapper mapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @PostMapping
    public ResponseEntity<PortfolioSelectionResponse> select(
            @Valid @RequestBody PortfolioSelectionRequest request) {
        int requestedSize = request.resolvedRequestedSize();
        SelectionTarget target = new SelectionTarget(
                request.getCareerTrack(),
                request.getAudienceRole().name(),
                request.getCapabilityCodes(),
                request.getGoal(),
                requestedSize);
        PortfolioSelectionResult result = service.select(target);
        return ResponseEntity.ok(mapper.map(result, target));
    }
}
