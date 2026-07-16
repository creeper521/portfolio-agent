package com.portfolio.agent.portfolio.domain.repository;

import com.portfolio.agent.portfolio.domain.model.PortfolioSnapshot;

public interface PublicPortfolioRepository {

    PortfolioSnapshot getSnapshot();
}
