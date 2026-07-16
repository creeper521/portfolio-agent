package com.portfolio.agent.portfolio.api;

import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.application.PortfolioQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioQueryService portfolioQueryService;

    public PortfolioController(PortfolioQueryService portfolioQueryService) {
        this.portfolioQueryService = portfolioQueryService;
    }

    @GetMapping("/portfolio")
    public PortfolioHomeResponse getPortfolio() {
        return portfolioQueryService.getPortfolio();
    }

    @GetMapping("/projects/{slug}")
    public ProjectDetailResponse getProject(@PathVariable String slug) {
        return portfolioQueryService.getProject(slug);
    }
}
