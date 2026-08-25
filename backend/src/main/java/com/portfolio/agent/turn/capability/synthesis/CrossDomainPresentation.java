package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.TaskPresentation;

import java.util.List;
import java.util.Objects;

/**
 * 跨域综合能力的展示产物，不可变 {@link TaskPresentation}：标题 + 恰好三个
 * 分节（通用原理 / 项目实例 / 概念与实例的关系），项目实例与关系分节携带
 * 公开来源引用列表。分节数量在构造时强制为 3，防止综合结构漂移。
 */
public final class CrossDomainPresentation implements TaskPresentation {
    private final String title;
    private final List<Section> sections;

    public CrossDomainPresentation(String title, List<Section> sections) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        this.title = title.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.size() != 3) throw new IllegalArgumentException("three sections are required");
    }
    public String getTitle() { return title; }
    public List<Section> getSections() { return sections; }

    /** 单个分节：封闭节类型 + 标题 + 正文 + 公开来源引用列表（可为空）。 */
    public record Section(
            AnswerSectionType sectionType, String title, String content,
            List<PublicSourceReferenceValue> sources) {
        public Section {
            Objects.requireNonNull(sectionType, "sectionType");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new IllegalArgumentException("section title/content are required");
            }
            title = title.trim();
            content = content.trim();
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        }
    }
}
