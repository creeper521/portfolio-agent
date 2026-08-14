package com.portfolio.agent.answer.general.domain;

import java.util.List;

public final class GeneralKnowledgeMetadata {
    private final String contentVersion;
    private final String audienceRole;
    private final List<String> discourseAliases;

    public GeneralKnowledgeMetadata(String contentVersion, String audienceRole, List<String> discourseAliases) {
        if (contentVersion == null || contentVersion.isBlank()) throw new IllegalArgumentException("contentVersion must not be blank");
        this.contentVersion = contentVersion.trim();
        this.audienceRole = audienceRole == null || audienceRole.isBlank() ? null : audienceRole.trim();
        this.discourseAliases = discourseAliases == null ? List.of() : List.copyOf(discourseAliases);
    }
    public String getContentVersion() { return contentVersion; }
    public String getAudienceRole() { return audienceRole; }
    public List<String> getDiscourseAliases() { return discourseAliases; }
}
