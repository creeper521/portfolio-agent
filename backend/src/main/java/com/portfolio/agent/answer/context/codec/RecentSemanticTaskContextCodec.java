package com.portfolio.agent.answer.context.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RecentSemanticTaskContextCodec implements ConversationContextCodec<RecentSemanticTaskContext> {
    public static final String SCHEMA_VERSION = "p3-recent-v1";
    private final ObjectMapper objectMapper;

    public RecentSemanticTaskContextCodec() { this(new ObjectMapper()); }
    RecentSemanticTaskContextCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public ConversationContextType getContextType() { return ConversationContextType.RECENT_SEMANTIC_TASK; }
    @Override public String getSchemaVersion() { return SCHEMA_VERSION; }

    @Override
    public byte[] encode(RecentSemanticTaskContext context) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("taskType", context.getTaskType().name());
            node.set("publicSubjects", subjects(context.getPublicSubjects()));
            node.set("facets", strings(context.getFacets()));
            node.set("dimensions", strings(context.getDimensions()));
            node.put("contentVersion", context.getContentVersion());
            node.put("sourceTaskId", context.getSourceTaskId());
            return objectMapper.writeValueAsBytes(node);
        } catch (Exception exception) {
            throw new IllegalStateException("context encoding is unavailable", exception);
        }
    }

    @Override
    public RecentSemanticTaskContext decode(byte[] payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            requireObject(node);
            rejectUnknown(node, Set.of("taskType", "publicSubjects", "facets", "dimensions", "contentVersion", "sourceTaskId"));
            return new RecentSemanticTaskContext(
                    enumValue(SemanticRoutingTypes.SemanticTaskType.class, node, "taskType"),
                    decodeSubjects(requiredArray(node, "publicSubjects")),
                    stringSet(requiredArray(node, "facets")), stringSet(requiredArray(node, "dimensions")),
                    requiredText(node, "contentVersion"), requiredText(node, "sourceTaskId"));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("context payload is invalid", exception);
        }
    }

    private ArrayNode subjects(List<SubjectReference> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.stream().sorted(java.util.Comparator.comparing(SubjectReference::getSubjectId))
                .forEach(subject -> {
                    ObjectNode node = array.addObject();
                    node.put("subjectType", subject.getSubjectType().name());
                    node.put("subjectId", subject.getSubjectId());
                    node.put("resolutionSource", subject.getResolutionSource().name());
                    if (subject.getContentVersion() != null) node.put("contentVersion", subject.getContentVersion());
                });
        return array;
    }
    private ArrayNode strings(Set<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        values.stream().sorted().forEach(array::add);
        return array;
    }
    private static List<SubjectReference> decodeSubjects(JsonNode array) {
        List<SubjectReference> values = new ArrayList<>();
        for (JsonNode item : array) {
            requireObject(item);
            values.add(new SubjectReference(
                    enumValue(SemanticRoutingTypes.SubjectType.class, item, "subjectType"),
                    requiredText(item, "subjectId"),
                    enumValue(SemanticRoutingTypes.SubjectResolutionSource.class, item, "resolutionSource"),
                    optionalText(item, "contentVersion")));
        }
        return List.copyOf(values);
    }
    private static Set<String> stringSet(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode value : array) {
            if (!value.isTextual() || value.asText().isBlank() || !values.add(value.asText())) throw new IllegalArgumentException("context payload is invalid");
        }
        return Set.copyOf(values);
    }
    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) throw new IllegalArgumentException("context payload is invalid");
        return value;
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
        return Enum.valueOf(type, requiredText(node, field));
    }
    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) throw new IllegalArgumentException("context payload is invalid");
        return value;
    }
    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("context payload is invalid");
        return value.asText().trim();
    }
    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("context payload is invalid");
    }
    private static void rejectUnknown(JsonNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new IllegalArgumentException("context payload is invalid"); });
    }
}
