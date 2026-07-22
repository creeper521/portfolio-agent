package com.portfolio.agent.answer.adapter.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.retrieval")
public final class RetrievalProperties {

    private RetrievalProfile profile = RetrievalProfile.DISABLED;
    private String modelDirectory = "";

    public RetrievalProfile getProfile() {
        return profile;
    }

    public void setProfile(RetrievalProfile profile) {
        this.profile = profile;
    }

    public String getModelDirectory() {
        return modelDirectory;
    }

    public void setModelDirectory(String modelDirectory) {
        this.modelDirectory = modelDirectory;
    }
}
