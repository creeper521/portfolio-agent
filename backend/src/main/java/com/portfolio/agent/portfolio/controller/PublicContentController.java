package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.PublicContentResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public-content")
public class PublicContentController {

    private final PortfolioService portfolioService;
    private final PortfolioResponseMapper responseMapper;

    public PublicContentController(
            PortfolioService portfolioService,
            PortfolioResponseMapper responseMapper
    ) {
        this.portfolioService = portfolioService;
        this.responseMapper = responseMapper;
    }

    @GetMapping
    public PublicContentResponse getPublicContent() {
        return responseMapper.toPublicContentResponse(portfolioService.getPublicContent());
    }
}
