package com.portfolio.agent.selection.dto;

import java.util.List;

public final class CapabilityCoverageResponse {

    private final String capabilityCode;
    private final String label;
    private final List<String> coveredBySubjectIds;

    public CapabilityCoverageResponse(
            String capabilityCode,
            String label,
            List<String> coveredBySubjectIds) {
        this.capabilityCode = capabilityCode;
        this.label = label;
        this.coveredBySubjectIds = List.copyOf(coveredBySubjectIds);
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getCoveredBySubjectIds() {
        return coveredBySubjectIds;
    }
}
