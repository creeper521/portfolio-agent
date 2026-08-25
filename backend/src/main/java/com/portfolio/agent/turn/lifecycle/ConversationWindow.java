package com.portfolio.agent.turn.lifecycle;

import java.util.List;
import java.util.Objects;

/**
 * 匿名会话窗口：严格 USER/ASSISTANT 交替的有限消息列表。
 *
 * <p>作为 {@code AgentTurnCommand} 的组成部分进入指纹与 Goal 解析输入，但属于
 * 禁止持久化的访客内容：任何 State 实现不得写入 ConversationWindow，日志与
 * toString 也只暴露消息数量。不可变，构造时即校验条数与角色交替不变量。</p>
 */
public final class ConversationWindow {

    public static final int MAX_MESSAGES = 40;
    public static final int MAX_MESSAGE_CHARACTERS = 4000;

    private final List<Message> messages;

    /**
     * 校验并复制消息列表。
     *
     * @throws IllegalArgumentException 超过 {@value #MAX_MESSAGES} 条，或角色不是
     *         从 USER 开始的严格 USER/ASSISTANT 交替
     */
    public ConversationWindow(List<Message> messages) {
        List<Message> copied = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (copied.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("conversation window contains too many messages");
        }
        for (int index = 0; index < copied.size(); index++) {
            Role expected = index % 2 == 0 ? Role.USER : Role.ASSISTANT;
            if (copied.get(index).getRole() != expected) {
                throw new IllegalArgumentException("conversation window must alternate USER and ASSISTANT");
            }
        }
        this.messages = copied;
    }

    /** 空窗口单例语义工厂。 */
    public static ConversationWindow empty() {
        return new ConversationWindow(List.of());
    }

    public List<Message> getMessages() {
        return messages;
    }

    /** 消息角色；窗口内必须从 USER 起始并与其交替出现。 */
    public enum Role {
        USER,
        ASSISTANT
    }

    /**
     * 单条会话消息。不可变；文本必须非空且不超过
     * {@value #MAX_MESSAGE_CHARACTERS} 字符，toString 对文本脱敏。
     */
    public static final class Message {
        private final Role role;
        private final String text;

        public Message(Role role, String text) {
            this.role = Objects.requireNonNull(role, "role");
            if (text == null || text.isBlank() || text.length() > MAX_MESSAGE_CHARACTERS) {
                throw new IllegalArgumentException("message text is required and bounded");
            }
            this.text = text;
        }

        public Role getRole() {
            return role;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Message that)) return false;
            return role == that.role && text.equals(that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(role, text);
        }

        @Override
        public String toString() {
            return "Message{role=" + role + ", text='<redacted>'}";
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConversationWindow that)) return false;
        return messages.equals(that.messages);
    }

    @Override
    public int hashCode() {
        return messages.hashCode();
    }

    @Override
    public String toString() {
        return "ConversationWindow{messageCount=" + messages.size() + '}';
    }
}
