package com.portfolio.agent.turn.capability.general;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GeneralDraftValidator {
    public GeneralSemanticResult validate(
            GeneralKnowledgeRequest request, GeneralDraftCodec.Draft draft) {
        if (!normalize(expectedTopic(request)).equals(normalize(draft.topic()))) {
            throw new IllegalArgumentException("draft topic does not match request");
        }
        List<GeneralSemanticResult.Statement> statements = draft.statements().stream()
                .map(value -> new GeneralSemanticResult.Statement(
                        value.role(), value.text(), value.subject(), value.dimension()))
                .toList();
        if (request.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION) {
            if (statements.size() != 2
                    || statements.get(0).getRole() != GeneralSemanticResult.Role.DEFINITION
                    || statements.get(1).getRole() != GeneralSemanticResult.Role.MECHANISM) {
                throw new IllegalArgumentException("explanation roles are invalid");
            }
        } else {
            validateComparisonCoverage(request, statements);
        }
        return new GeneralSemanticResult(
                draft.topic(), statements, draft.caveats(), request.getExpectedContentVersion());
    }

    private void validateComparisonCoverage(
            GeneralKnowledgeRequest request, List<GeneralSemanticResult.Statement> statements) {
        if (statements.stream().anyMatch(value -> value.getRole() != GeneralSemanticResult.Role.COMPARISON)) {
            throw new IllegalArgumentException("comparison contains an invalid role");
        }
        Set<String> actual = new HashSet<>();
        for (GeneralSemanticResult.Statement statement : statements) {
            actual.add(normalize(statement.getSubject()) + "\u0000" + normalize(statement.getDimension()));
        }
        for (String subject : request.getSubjects()) {
            for (String dimension : request.getDimensions()) {
                if (!actual.contains(normalize(subject) + "\u0000" + normalize(dimension))) {
                    throw new IllegalArgumentException("comparison coverage is incomplete");
                }
            }
        }
    }

    private String expectedTopic(GeneralKnowledgeRequest request) {
        return request.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION
                ? request.getTopic() : String.join(" vs ", request.getSubjects());
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
