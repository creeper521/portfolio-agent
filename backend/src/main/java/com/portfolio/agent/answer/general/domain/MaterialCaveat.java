package com.portfolio.agent.answer.general.domain;

import java.util.Objects;

public final class MaterialCaveat {
    private final String alias;
    private final String text;

    public MaterialCaveat(String alias, String text) {
        this.alias = requireText(alias, "alias");
        this.text = requireText(text, "text");
    }
    public String getAlias() { return alias; }
    public String getText() { return text; }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MaterialCaveat that)) return false;
        return alias.equals(that.alias) && text.equals(that.text);
    }
    @Override public int hashCode() { return Objects.hash(alias, text); }
}
