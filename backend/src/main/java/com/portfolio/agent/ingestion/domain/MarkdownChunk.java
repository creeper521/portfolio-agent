package com.portfolio.agent.ingestion.domain;

import java.util.Objects;

public final class MarkdownChunk {

    private final int ordinal;
    private final String text;
    private final String hash;

    public MarkdownChunk(int ordinal, String text, String hash) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("chunk ordinal must not be negative");
        }
        this.ordinal = ordinal;
        this.text = Objects.requireNonNull(text, "text");
        this.hash = Objects.requireNonNull(hash, "hash");
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getText() {
        return text;
    }

    public String getHash() {
        return hash;
    }
}
