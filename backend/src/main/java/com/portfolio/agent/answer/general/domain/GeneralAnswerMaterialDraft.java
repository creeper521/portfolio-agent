package com.portfolio.agent.answer.general.domain;

import java.util.List;
import java.util.Set;

public final class GeneralAnswerMaterialDraft {
    private final String topic;
    private final List<StatementDraft> statements;
    private final List<CaveatDraft> caveats;
    private final MetadataDraft metadata;

    public GeneralAnswerMaterialDraft(String topic, List<StatementDraft> statements,
                                      List<CaveatDraft> caveats, MetadataDraft metadata) {
        this.topic = topic;
        this.statements = statements == null ? List.of() : List.copyOf(statements);
        this.caveats = caveats == null ? List.of() : List.copyOf(caveats);
        this.metadata = metadata;
    }
    public String getTopic() { return topic; }
    public List<StatementDraft> getStatements() { return statements; }
    public List<CaveatDraft> getCaveats() { return caveats; }
    public MetadataDraft getMetadata() { return metadata; }

    public static final class StatementDraft {
        private final String statementAlias;
        private final String text;
        private final GeneralStatementRole role;
        private final Set<String> conceptTags;
        private final GeneralSupportKind supportKind;
        private final List<String> publicSourceKeys;
        public StatementDraft(String statementAlias, String text, GeneralStatementRole role,
                              Set<String> conceptTags, GeneralSupportKind supportKind,
                              List<String> publicSourceKeys) {
            this.statementAlias = statementAlias; this.text = text; this.role = role;
            this.conceptTags = conceptTags == null ? Set.of() : Set.copyOf(conceptTags);
            this.supportKind = supportKind;
            this.publicSourceKeys = publicSourceKeys == null ? List.of() : List.copyOf(publicSourceKeys);
        }
        public String getStatementAlias() { return statementAlias; }
        public String getText() { return text; }
        public GeneralStatementRole getRole() { return role; }
        public Set<String> getConceptTags() { return conceptTags; }
        public GeneralSupportKind getSupportKind() { return supportKind; }
        public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    }
    public static final class CaveatDraft {
        private final String alias; private final String text;
        public CaveatDraft(String alias, String text) { this.alias = alias; this.text = text; }
        public String getAlias() { return alias; }
        public String getText() { return text; }
    }
    public static final class MetadataDraft {
        private final String contentVersion; private final String audienceRole; private final List<String> discourseAliases;
        public MetadataDraft(String contentVersion, String audienceRole, List<String> discourseAliases) {
            this.contentVersion = contentVersion; this.audienceRole = audienceRole;
            this.discourseAliases = discourseAliases == null ? List.of() : List.copyOf(discourseAliases);
        }
        public String getContentVersion() { return contentVersion; }
        public String getAudienceRole() { return audienceRole; }
        public List<String> getDiscourseAliases() { return discourseAliases; }
    }
}
