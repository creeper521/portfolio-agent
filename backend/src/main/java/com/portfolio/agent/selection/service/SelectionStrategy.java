package com.portfolio.agent.selection.service;

import com.portfolio.agent.selection.domain.PortfolioSelection;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.List;

public interface SelectionStrategy {

    PortfolioSelection select(SelectionTarget target, List<SelectionCandidate> candidates);

    String policyVersion();
}
