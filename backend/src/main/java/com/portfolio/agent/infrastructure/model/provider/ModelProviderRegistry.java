package com.portfolio.agent.infrastructure.model.provider;

public interface ModelProviderRegistry {

    String getSnapshotVersion();

    boolean isApprovedConfiguration(
            ModelProviderKind provider,
            String modelPolicyVersion,
            String answerSchemaVersion);
}
