package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;

    public PortfolioController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/portfolio")
    public PortfolioHomeResponse getPortfolio() {
        return responseMapper.toPortfolioResponse(portfolioService.getPortfolio());
    }

    @GetMapping("/projects/{slug}")
    public ProjectDetailResponse getProject(@PathVariable String slug) {
        return responseMapper.toProjectResponse(portfolioService.getProject(slug));
    }
}
