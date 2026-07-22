package com.portfolio.agent.answer.domain;

import java.util.List;

public final class RuntimeAnswerContent {

    private final String contentVersion;
    private final String runtimeBundleHash;
    private final List<AnswerKnowledge> projects;

    public RuntimeAnswerContent(
            String contentVersion,
            String runtimeBundleHash,
            List<AnswerKnowledge> projects
    ) {
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.projects = List.copyOf(projects);
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public String getRuntimeBundleHash() {
        return runtimeBundleHash;
    }

    public List<AnswerKnowledge> getProjects() {
        return projects;
    }
}
