package com.portfolio.agent.portfolio.repository;

import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

public interface PublicPortfolioRepository {

    RuntimeContentSnapshot getSnapshot();
}
