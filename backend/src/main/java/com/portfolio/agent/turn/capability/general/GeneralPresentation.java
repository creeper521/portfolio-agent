package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.TaskPresentation;

import java.util.List;
import java.util.Objects;

/**
 * 通用能力的展示产物，不可变 {@link TaskPresentation}：标题加分节列表，
 * 每节携带封闭的 {@link AnswerSectionType} 与标题、正文。构造时要求标题非空、
 * 分节非空列表；由 {@link GeneralPresentationComposer} 从语义结果组装。
 */
public final class GeneralPresentation implements TaskPresentation {
    private final String title;
    private final List<Section> sections;

    public GeneralPresentation(String title, List<Section> sections) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        this.title = title.trim();
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.isEmpty()) throw new IllegalArgumentException("sections are required");
    }
    public String getTitle() { return title; }
    public List<Section> getSections() { return sections; }

    /** 单个展示分节：封闭节类型 + 标题 + 正文，构造时校验非空并去除首尾空白。 */
    public record Section(AnswerSectionType sectionType, String title, String content) {
        public Section {
            Objects.requireNonNull(sectionType, "sectionType");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                throw new IllegalArgumentException("section title/content are required");
            }
            title = title.trim();
            content = content.trim();
        }
    }
}
