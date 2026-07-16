package com.portfolio.agent.portfolio.repository;

import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;

public interface PublicPortfolioRepository {

    PortfolioSnapshot getSnapshot();
}
