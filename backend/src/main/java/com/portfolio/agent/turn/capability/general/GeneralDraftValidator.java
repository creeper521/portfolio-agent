package com.portfolio.agent.turn.capability.general;

import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GeneralDraftValidator {
    public GeneralSemanticResult validate(
            GeneralKnowledgeRequest request, GeneralDraftCodec.Draft draft) {
        if (!expectedTopic(request).equals(draft.topic())) {
            reject(GeneralDraftValidationException.Reason.TOPIC_MISMATCH,
                    "draft topic does not match request");
        }
        List<GeneralSemanticResult.Statement> statements = draft.statements().stream()
                .map(value -> new GeneralSemanticResult.Statement(
                        value.role(), value.text(), value.subject(), value.dimension()))
                .toList();
        if (request.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION) {
            if (statements.size() != 2
                    || statements.get(0).getRole() != GeneralSemanticResult.Role.DEFINITION
                    || statements.get(1).getRole() != GeneralSemanticResult.Role.MECHANISM) {
                reject(GeneralDraftValidationException.Reason.EXPLANATION_ROLES_INVALID,
                        "explanation roles are invalid");
            }
            validateExplanationQuality(request, draft);
        } else {
            validateComparisonCoverage(request, statements);
            if (draft.statements().stream().anyMatch(value -> !value.aspects().isEmpty())) {
                reject(GeneralDraftValidationException.Reason.COMPARISON_ASPECTS_INVALID,
                        "comparison aspects must be empty");
            }
            statements.forEach(statement -> validateChineseSentences(
                    statement.getText(), 1, 3, "comparison text"));
        }
        validateCaveats(draft.caveats());
        return new GeneralSemanticResult(
                draft.topic(), statements,
                draft.caveats().stream().map(GeneralDraftCodec.CaveatDraft::text).toList(),
                request.getExpectedContentVersion());
    }

    private void validateExplanationQuality(
            GeneralKnowledgeRequest request, GeneralDraftCodec.Draft draft) {
        int minimum = switch (request.getDepth()) {
            case CONCISE -> 1;
            case STANDARD -> 2;
            case DETAILED -> 4;
        };
        int maximum = request.getDepth() == com.portfolio.agent.turn.planning
                .UserGoalProposal.Depth.STANDARD ? 3 : minimum;
        draft.statements().forEach(statement -> validateChineseSentences(
                statement.text(), minimum, maximum, "explanation text"));
        if (!draft.statements().get(0).aspects().contains(GeneralDraftCodec.Aspect.DEFINITION)
                || !draft.statements().get(1).aspects().contains(
                GeneralDraftCodec.Aspect.MECHANISM)) {
            reject(GeneralDraftValidationException.Reason.EXPLANATION_ROLE_ASPECTS_INVALID,
                    "explanation role aspects are invalid");
        }
        Set<GeneralDraftCodec.Aspect> actual = EnumSet.noneOf(GeneralDraftCodec.Aspect.class);
        draft.statements().forEach(statement -> actual.addAll(statement.aspects()));
        Set<GeneralDraftCodec.Aspect> expected = switch (request.getDepth()) {
            case CONCISE -> EnumSet.of(
                    GeneralDraftCodec.Aspect.DEFINITION,
                    GeneralDraftCodec.Aspect.MECHANISM);
            case STANDARD -> EnumSet.of(
                    GeneralDraftCodec.Aspect.DEFINITION,
                    GeneralDraftCodec.Aspect.MECHANISM,
                    GeneralDraftCodec.Aspect.TYPICAL_USAGE,
                    GeneralDraftCodec.Aspect.APPLICABILITY_BOUNDARY);
            case DETAILED -> EnumSet.allOf(GeneralDraftCodec.Aspect.class);
        };
        if (!actual.equals(expected)) {
            reject(GeneralDraftValidationException.Reason.EXPLANATION_COVERAGE_INVALID,
                    "explanation semantic coverage is invalid");
        }
    }

    private void validateComparisonCoverage(
            GeneralKnowledgeRequest request, List<GeneralSemanticResult.Statement> statements) {
        if (statements.stream().anyMatch(value -> value.getRole() != GeneralSemanticResult.Role.COMPARISON)) {
            reject(GeneralDraftValidationException.Reason.COMPARISON_ROLE_INVALID,
                    "comparison contains an invalid role");
        }
        Set<String> expected = new HashSet<>();
        for (String subject : request.getSubjects()) {
            for (String dimension : request.getDimensions()) {
                expected.add(subject + "\u0000" + dimension);
            }
        }
        Set<String> actual = new HashSet<>();
        for (GeneralSemanticResult.Statement statement : statements) {
            String pair = statement.getSubject() + "\u0000" + statement.getDimension();
            if (!actual.add(pair)) {
                reject(GeneralDraftValidationException.Reason.COMPARISON_DUPLICATE_PAIR,
                        "comparison contains a duplicate pair");
            }
        }
        if (!actual.equals(expected) || statements.size() != expected.size()) {
            reject(GeneralDraftValidationException.Reason.COMPARISON_COVERAGE_INVALID,
                    "comparison pairs do not match request");
        }
    }

    private void validateCaveats(List<GeneralDraftCodec.CaveatDraft> caveats) {
        Set<String> texts = new HashSet<>();
        Set<GeneralDraftCodec.CaveatKind> kinds = EnumSet.noneOf(
                GeneralDraftCodec.CaveatKind.class);
        for (GeneralDraftCodec.CaveatDraft caveat : caveats) {
            validateChineseSentences(caveat.text(), 1, 2, "caveat");
            if (!texts.add(normalize(caveat.text())) || !kinds.add(caveat.kind())) {
                reject(GeneralDraftValidationException.Reason.CAVEAT_DUPLICATE,
                        "caveats contain duplicates");
            }
        }
    }

    private void validateChineseSentences(
            String text, int minimum, int maximum, String name) {
        if (!text.endsWith("。") || text.matches(".*[.!?！？；;].*")) {
            reject(GeneralDraftValidationException.Reason.SENTENCE_BOUNDARY_INVALID,
                    name + " has invalid sentence boundaries");
        }
        List<String> sentences = java.util.Arrays.stream(text.split("。", -1))
                .filter(value -> !value.isBlank()).toList();
        if (sentences.size() < minimum || sentences.size() > maximum
                || sentences.stream().anyMatch(value -> !value.matches(".*[\\p{IsHan}].*"))) {
            reject(GeneralDraftValidationException.Reason.LANGUAGE_OR_SENTENCE_COUNT_INVALID,
                    name + " has invalid language or sentence count");
        }
    }

    private void reject(GeneralDraftValidationException.Reason reason, String message) {
        throw new GeneralDraftValidationException(reason, message);
    }

    private String expectedTopic(GeneralKnowledgeRequest request) {
        return request.getKind() == GeneralKnowledgeRequest.Kind.EXPLANATION
                ? request.getTopic() : String.join(" vs ", request.getSubjects());
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
