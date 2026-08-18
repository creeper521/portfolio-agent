package com.portfolio.agent.turn.lifecycle;

import java.util.List;
import java.util.Objects;

public final class ConversationWindow {

    public static final int MAX_MESSAGES = 40;
    public static final int MAX_MESSAGE_CHARACTERS = 4000;

    private final List<Message> messages;

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

    public static ConversationWindow empty() {
        return new ConversationWindow(List.of());
    }

    public List<Message> getMessages() {
        return messages;
    }

    public enum Role {
        USER,
        ASSISTANT
    }

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
