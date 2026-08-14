package com.portfolio.agent.answer.general.domain;

import java.util.List;

public final class GeneralAnswerMaterial {
    private final String topic;
    private final List<GeneralStatement> statements;
    private final List<MaterialCaveat> caveats;
    private final GeneralKnowledgeMetadata metadata;

    public GeneralAnswerMaterial(String topic, List<GeneralStatement> statements,
                                 List<MaterialCaveat> caveats, GeneralKnowledgeMetadata metadata) {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic must not be blank");
        if (statements == null || statements.isEmpty()) throw new IllegalArgumentException("statements must not be empty");
        this.topic = topic.trim();
        this.statements = List.copyOf(statements);
        this.caveats = caveats == null ? List.of() : List.copyOf(caveats);
        this.metadata = metadata;
    }
    public String getTopic() { return topic; }
    public List<GeneralStatement> getStatements() { return statements; }
    public List<MaterialCaveat> getCaveats() { return caveats; }
    public GeneralKnowledgeMetadata getMetadata() { return metadata; }
}
