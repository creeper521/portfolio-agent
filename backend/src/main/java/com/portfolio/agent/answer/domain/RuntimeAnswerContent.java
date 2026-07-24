package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Optional;

public final class RuntimeAnswerContent {

    private final String contentVersion;
    private final String runtimeBundleHash;
    private final List<AnswerKnowledge> projects;
    private final List<AnswerKnowledge> cases;
    private final AnswerRetrievalCorpus retrievalCorpus;
    private final List<AnswerTimelineEvent> timeline;
    private final RuntimeCapabilities capabilities;

    public RuntimeAnswerContent(
            String contentVersion,
            String runtimeBundleHash,
            List<AnswerKnowledge> projects
    ) {
        this(contentVersion, runtimeBundleHash, projects, List.of(), null, List.of());
    }

    public RuntimeAnswerContent(
            String contentVersion,
            String runtimeBundleHash,
            List<AnswerKnowledge> projects,
            AnswerRetrievalCorpus retrievalCorpus
    ) {
        this(contentVersion, runtimeBundleHash, projects, List.of(), retrievalCorpus, List.of());
    }

    public RuntimeAnswerContent(
            String contentVersion,
            String runtimeBundleHash,
            List<AnswerKnowledge> projects,
            AnswerRetrievalCorpus retrievalCorpus,
            List<AnswerTimelineEvent> timeline
    ) {
        this(contentVersion, runtimeBundleHash, projects, List.of(), retrievalCorpus, timeline);
    }

    public RuntimeAnswerContent(
            String contentVersion,
            String runtimeBundleHash,
            List<AnswerKnowledge> projects,
            List<AnswerKnowledge> cases,
            AnswerRetrievalCorpus retrievalCorpus,
            List<AnswerTimelineEvent> timeline
    ) {
        boolean presetAnswers = java.util.stream.Stream.concat(projects.stream(), cases.stream())
                .anyMatch(project -> !project.getQuestions().isEmpty());
        boolean stableReferences = java.util.stream.Stream.concat(projects.stream(), cases.stream())
                .anyMatch(project -> !project.getClaims().isEmpty());
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.projects = List.copyOf(projects);
        this.cases = List.copyOf(cases);
        this.retrievalCorpus = retrievalCorpus;
        this.timeline = List.copyOf(timeline);
        this.capabilities = new RuntimeCapabilities(
                presetAnswers,
                false,
                retrievalCorpus != null,
                stableReferences,
                stableReferences);
    }

    public RuntimeAnswerContent(
            String contentVersion,
            String runtimeBundleHash,
            List<AnswerKnowledge> projects,
            AnswerRetrievalCorpus retrievalCorpus,
            List<AnswerTimelineEvent> timeline,
            RuntimeCapabilities capabilities
    ) {
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.projects = List.copyOf(projects);
        this.cases = List.of();
        this.retrievalCorpus = retrievalCorpus;
        this.timeline = List.copyOf(timeline);
        this.capabilities = java.util.Objects.requireNonNull(capabilities, "capabilities");
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

    public List<AnswerKnowledge> getCases() { return cases; }

    public Optional<AnswerRetrievalCorpus> getRetrievalCorpus() {
        return Optional.ofNullable(retrievalCorpus);
    }

    public List<AnswerTimelineEvent> getTimeline() {
        return timeline;
    }

    public RuntimeCapabilities getCapabilities() {
        return capabilities;
    }
}
