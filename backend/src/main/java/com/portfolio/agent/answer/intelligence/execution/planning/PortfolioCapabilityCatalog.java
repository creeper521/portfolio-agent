package com.portfolio.agent.answer.intelligence.execution.planning;

import java.util.List;
import java.util.Objects;

/** Startup-frozen catalog for the single P3 v1 business capability. */
public final class PortfolioCapabilityCatalog {

    public static final String CAPABILITY_ID = "PORTFOLIO_EVIDENCE_RETRIEVAL_V1";
    public static final String CAPABILITY_VERSION = "v1";

    private final List<CapabilityDescriptor> descriptors;

    public PortfolioCapabilityCatalog() {
        this.descriptors = List.of(new CapabilityDescriptor(
                CAPABILITY_ID, CAPABILITY_VERSION, false,
                List.of("PORTFOLIO_FACT", "PORTFOLIO_COMPARE",
                        "PORTFOLIO_RECOMMEND", "PORTFOLIO_REFINE_RECOMMENDATION")));
    }

    public List<CapabilityDescriptor> getDescriptors() {
        return descriptors;
    }

    public CapabilityDescriptor getPortfolioEvidenceRetrievalV1() {
        return descriptors.getFirst();
    }

    public boolean contains(String capabilityId) {
        return CAPABILITY_ID.equals(capabilityId);
    }

    public static final class CapabilityDescriptor {

        private final String capabilityId;
        private final String version;
        private final boolean writable;
        private final List<String> supportedTaskTypes;

        private CapabilityDescriptor(
                String capabilityId, String version, boolean writable, List<String> supportedTaskTypes) {
            this.capabilityId = requireText(capabilityId, "capabilityId");
            this.version = requireText(version, "version");
            this.writable = writable;
            this.supportedTaskTypes = List.copyOf(
                    Objects.requireNonNull(supportedTaskTypes, "supportedTaskTypes"));
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public String getVersion() {
            return version;
        }

        public boolean isReadOnly() {
            return !writable;
        }

        public boolean isWritable() {
            return writable;
        }

        public List<String> getSupportedTaskTypes() {
            return supportedTaskTypes;
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }
    }
}
