package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Strict decoder for untrusted provider output. Unknown or shape-mismatched fields fail closed. */
public final class GeneralDraftCodec {
    public static final String SCHEMA_VERSION = "general.draft.v1";
    private static final Set<String> ROOT_FIELDS = Set.of("topic", "statements", "caveats");
    private static final Set<String> STATEMENT_FIELDS = Set.of("role", "text", "subject", "dimension");
    private final ObjectMapper objectMapper;

    public GeneralDraftCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    public Draft decode(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            requireObject(root, ROOT_FIELDS, "root");
            String topic = requiredText(root, "topic");
            JsonNode statementsNode = root.get("statements");
            if (statementsNode == null || !statementsNode.isArray()
                    || statementsNode.isEmpty() || statementsNode.size() > 20) {
                throw new IllegalArgumentException("statements are invalid");
            }
            List<StatementDraft> statements = new ArrayList<>();
            for (JsonNode node : statementsNode) {
                requireObject(node, STATEMENT_FIELDS, "statement");
                GeneralSemanticResult.Role role = GeneralSemanticResult.Role.valueOf(requiredText(node, "role"));
                statements.add(new StatementDraft(
                        role, requiredText(node, "text"), optionalText(node, "subject"),
                        optionalText(node, "dimension")));
            }
            JsonNode caveatsNode = root.get("caveats");
            if (caveatsNode == null || !caveatsNode.isArray() || caveatsNode.size() > 10) {
                throw new IllegalArgumentException("caveats are invalid");
            }
            List<String> caveats = new ArrayList<>();
            for (JsonNode node : caveatsNode) {
                if (!node.isTextual() || node.textValue().isBlank() || node.textValue().length() > 1000) {
                    throw new IllegalArgumentException("caveat is invalid");
                }
                caveats.add(node.textValue().trim());
            }
            return new Draft(topic, statements, caveats);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("general draft is invalid", exception);
        }
    }

    private void requireObject(JsonNode node, Set<String> allowed, String name) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(name + " must be an object");
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) throw new IllegalArgumentException(name + " contains unknown fields");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 4000) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.textValue().trim();
    }

    public record Draft(String topic, List<StatementDraft> statements, List<String> caveats) {
        public Draft {
            statements = List.copyOf(statements);
            caveats = List.copyOf(caveats);
        }
    }
    public record StatementDraft(
            GeneralSemanticResult.Role role, String text, String subject, String dimension) { }
}
