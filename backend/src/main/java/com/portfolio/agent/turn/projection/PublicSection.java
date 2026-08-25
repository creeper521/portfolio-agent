package com.portfolio.agent.turn.projection;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import java.util.Objects;

/**
 * 公众回答的单个分节：稳定 sectionId、类型、标题、内容与支撑声明。
 *
 * <p>REJECTED 类型的节不允许出现在公众回答中（构造期拒绝）；sectionId 遵循
 * 公开 slug 字符集。</p>
 */
public final class PublicSection {
    private final String sectionId;
    private final AnswerSectionType sectionKind;
    private final String title;
    private final String content;
    private final PublicSupport support;

    public PublicSection(
            String sectionId, AnswerSectionType sectionKind, String title,
            String content, PublicSupport support) {
        this.sectionId = id(sectionId);
        this.sectionKind = Objects.requireNonNull(sectionKind, "sectionKind");
        if (sectionKind == AnswerSectionType.REJECTED) {
            throw new IllegalArgumentException("rejected is not a public section kind");
        }
        this.title = text(title, "title");
        this.content = text(content, "content");
        this.support = Objects.requireNonNull(support, "support");
    }
    public String getSectionId() { return sectionId; }
    public AnswerSectionType getSectionKind() { return sectionKind; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public PublicSupport getSupport() { return support; }

    private static String id(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{1,95}")) {
            throw new IllegalArgumentException("sectionId is invalid");
        }
        return value;
    }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
