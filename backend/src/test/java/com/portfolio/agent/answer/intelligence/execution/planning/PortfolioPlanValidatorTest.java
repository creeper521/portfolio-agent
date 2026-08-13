package com.portfolio.agent.answer.intelligence.execution.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioPlanValidatorTest {

    @Test
    void trustedWrapperIsIssuedOnlyForCatalogKnownPlan() {
        PortfolioCapabilityCatalog catalog = new PortfolioCapabilityCatalog();
        PortfolioExecutionPlanner planner = new PortfolioExecutionPlanner(catalog);
        PortfolioPlanValidator validator = new PortfolioPlanValidator(catalog);
        PortfolioExecutionPlan plan = planner.plan(PortfolioPlannerFixtures.factContext());

        TrustedPortfolioExecutionPlan trusted = validator.validate(plan, PortfolioPlannerFixtures.factContext());

        assertEquals(plan, trusted.getPlan());
    }
}
