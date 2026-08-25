package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.TaskSemanticResult;

import java.util.List;
import java.util.Objects;

/**
 * 通用能力的语义结果，不可变 {@link TaskSemanticResult}：通过
 * {@link GeneralDraftValidator} 校验后的最终产出——主题、陈述列表、caveat 列表
 * 与绑定的内容版本。这是可跨依赖边传递的类型化数据，不是渲染文本。
 */
public final class GeneralSemanticResult implements TaskSemanticResult {
    private final String topic;
    private final List<Statement> statements;
    private final List<String> caveats;
    private final String contentVersion;

    public GeneralSemanticResult(
            String topic, List<Statement> statements,
            List<String> caveats, String contentVersion) {
        this.topic = requireText(topic, "topic");
        this.statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
        if (this.statements.isEmpty()) throw new IllegalArgumentException("statements are required");
        this.caveats = List.copyOf(Objects.requireNonNull(caveats, "caveats"));
        this.caveats.forEach(value -> requireText(value, "caveat"));
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    public String getTopic() { return topic; }
    public List<Statement> getStatements() { return statements; }
    public List<String> getCaveats() { return caveats; }
    public String getContentVersion() { return contentVersion; }

    /**
     * 单条陈述，不可变：角色 + 正文，COMPARISON 角色还必须携带 subject 与
     * dimension，非对比角色则禁止携带这两个字段（构造时互斥校验）。
     */
    public static final class Statement {
        private final Role role;
        private final String text;
        private final String subject;
        private final String dimension;

        public Statement(Role role, String text, String subject, String dimension) {
            this.role = Objects.requireNonNull(role, "role");
            this.text = requireText(text, "text");
            this.subject = subject == null ? null : requireText(subject, "subject");
            this.dimension = dimension == null ? null : requireText(dimension, "dimension");
            if (role == Role.COMPARISON && (this.subject == null || this.dimension == null)) {
                throw new IllegalArgumentException("comparison statement requires subject and dimension");
            }
            if (role != Role.COMPARISON && (this.subject != null || this.dimension != null)) {
                throw new IllegalArgumentException("explanation statement cannot carry comparison fields");
            }
        }

        public Role getRole() { return role; }
        public String getText() { return text; }
        public String getSubject() { return subject; }
        public String getDimension() { return dimension; }
    }

    /** 陈述角色：DEFINITION 定义、MECHANISM 机制、COMPARISON 对比单元格。 */
    public enum Role { DEFINITION, MECHANISM, COMPARISON }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 4000) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
