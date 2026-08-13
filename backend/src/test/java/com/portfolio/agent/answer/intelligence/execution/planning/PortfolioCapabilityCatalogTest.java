package com.portfolio.agent.answer.intelligence.execution.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PortfolioCapabilityCatalogTest {

    @Test
    void startupCatalogContainsExactlyOneReadOnlyCapability() {
        PortfolioCapabilityCatalog catalog = new PortfolioCapabilityCatalog();

        assertEquals(1, catalog.getDescriptors().size());
        assertEquals("PORTFOLIO_EVIDENCE_RETRIEVAL_V1",
                catalog.getDescriptors().getFirst().getCapabilityId());
        assertFalse(catalog.getDescriptors().getFirst().isWritable());
    }
}
