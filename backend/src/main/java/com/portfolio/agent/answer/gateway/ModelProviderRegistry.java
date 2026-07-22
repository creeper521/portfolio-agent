package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.ModelProviderKind;

public interface ModelProviderRegistry {

    String getSnapshotVersion();

    boolean supports(
            ModelProviderKind provider,
            String modelPolicyVersion,
            String answerSchemaVersion);
}
