package com.portfolio.agent.turn.projection;

import java.util.List;
import java.util.Objects;

public final class PublicSupport {
    private final Kind kind;
    private final List<String> publicSourceKeys;

    public PublicSupport(Kind kind, List<String> publicSourceKeys) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.publicSourceKeys = List.copyOf(Objects.requireNonNull(publicSourceKeys, "publicSourceKeys"));
        if (this.publicSourceKeys.stream().anyMatch(value -> value == null || value.isBlank())
                || this.publicSourceKeys.stream().distinct().count() != this.publicSourceKeys.size()) {
            throw new IllegalArgumentException("publicSourceKeys are invalid");
        }
        if (kind == Kind.VERIFIED_PUBLIC_EVIDENCE && this.publicSourceKeys.isEmpty()) {
            throw new IllegalArgumentException("verified support requires public sources");
        }
        if (kind == Kind.GENERAL_KNOWLEDGE && !this.publicSourceKeys.isEmpty()) {
            throw new IllegalArgumentException("general knowledge cannot claim public evidence");
        }
    }

    public Kind getKind() { return kind; }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    public enum Kind { GENERAL_KNOWLEDGE, VERIFIED_PUBLIC_EVIDENCE, DERIVED }
}
