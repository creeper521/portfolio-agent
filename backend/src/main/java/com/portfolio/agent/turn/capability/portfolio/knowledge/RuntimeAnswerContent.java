package com.portfolio.agent.turn.capability.portfolio.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * 运行时回答知识内容（不可变值对象）：一次公开快照在回答层的完整投影。
 *
 * <p>包含内容版本与运行时包哈希、项目与案例知识、可选的本地检索语料、
 * 公开时间线，以及能力开关。派生构造器的能力规则：任一主体带预设问题即启用
 * presetAnswers，带 claim 即启用稳定引用类能力（readOnlyTools 与
 * multiTurnReferences），检索语料存在才启用 groundedQuestions，
 * modelExpression 固定为 false；显式传入 {@link RuntimeCapabilities}
 * 的构造器则完全由调用方决定。多个便捷构造器用于省略案例/时间线的兼容场景。
 */
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
        // 能力开关按内容是否存在派生：有预设问题才有 presetAnswers，有 claim 才有稳定引用
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
