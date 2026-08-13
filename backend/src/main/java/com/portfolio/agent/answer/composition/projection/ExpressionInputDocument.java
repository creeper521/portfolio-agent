package com.portfolio.agent.answer.composition.projection;

import java.util.Objects;

public final class ExpressionInputDocument {
    private final String serializedJson;
    private final ExpressionAliasRegistry aliases;
    private final boolean overLimit;
    public ExpressionInputDocument(String serializedJson, ExpressionAliasRegistry aliases, boolean overLimit) { this.serializedJson = Objects.requireNonNull(serializedJson); this.aliases = Objects.requireNonNull(aliases); this.overLimit = overLimit; }
    public String getSerializedJson() { return serializedJson; }
    public ExpressionAliasRegistry getAliases() { return aliases; }
    public boolean isOverLimit() { return overLimit; }
    @Override public String toString() { return "ExpressionInputDocument{size=" + serializedJson.length() + ", overLimit=" + overLimit + "}"; }
}
