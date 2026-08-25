package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.TaskPresentation;

import java.util.List;
import java.util.Objects;

/**
 * 作品集任务的呈现结果（不可变值对象）：标题加有序的公开回答段落，实现 {@link TaskPresentation}。
 *
 * <p>不变量：标题与段落均不可为空；每个段落必须有类型、标题、正文和至少一条
 * 公开来源引用（sources 为空即拒绝），保证呈现层不出现无出处的结论。
 */
public final class PortfolioPresentation implements TaskPresentation {
    private final String title;
    private final List<Section> sections;
    public PortfolioPresentation(String title, List<Section> sections) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        this.title = title.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.isEmpty()) throw new IllegalArgumentException("sections are required");
    }
    public String getTitle() { return title; }
    public List<Section> getSections() { return sections; }

    /** 呈现中的单个段落：类型、标题、正文与公开来源引用；构造时强制来源非空。 */
    public static final class Section {
        private final AnswerSectionType sectionType;
        private final String title;
        private final String content;
        private final List<PublicSourceReferenceValue> sources;
        public Section(
                AnswerSectionType sectionType, String title, String content,
                List<PublicSourceReferenceValue> sources) {
            this.sectionType = Objects.requireNonNull(sectionType, "sectionType");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new IllegalArgumentException("section title/content are required");
            }
            this.title = title.trim();
            this.content = content.trim();
            this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            if (this.sources.isEmpty()) throw new IllegalArgumentException("portfolio section requires sources");
        }
        public AnswerSectionType getSectionType() { return sectionType; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public List<PublicSourceReferenceValue> getSources() { return sources; }
    }
}
