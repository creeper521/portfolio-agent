package com.portfolio.agent.answer.context.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RecommendationContextCodec implements ConversationContextCodec<RecommendationContext> {
    public static final String SCHEMA_VERSION = "p3-recommendation-v1";
    private final ObjectMapper objectMapper;
    public RecommendationContextCodec() { this(new ObjectMapper()); }
    RecommendationContextCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override public ConversationContextType getContextType() { return ConversationContextType.RECOMMENDATION; }
    @Override public String getSchemaVersion() { return SCHEMA_VERSION; }

    @Override
    public byte[] encode(RecommendationContext context) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            ObjectNode scope = node.putObject("authorizedScope");
            scope.put("mode", context.getAuthorizedScope().getMode().name());
            scope.put("contentVersion", context.getAuthorizedScope().getContentVersion());
            ArrayNode exact = scope.putArray("exactSubjects");
            context.getAuthorizedScope().getExactSubjects().stream()
                    .sorted(java.util.Comparator.comparing(SubjectReference::getSubjectId)).forEach(subject -> {
                        ObjectNode item = exact.addObject();
                        item.put("subjectType", subject.getSubjectType().name());
                        item.put("subjectId", subject.getSubjectId());
                        item.put("resolutionSource", subject.getResolutionSource().name());
                        if (subject.getContentVersion() != null) item.put("contentVersion", subject.getContentVersion());
                    });
            node.put("profileVersion", context.getProfileVersion());
            node.set("baselineCriteria", strings(context.getBaselineCriteria()));
            node.set("constraints", strings(context.getConstraints()));
            node.set("preferences", strings(context.getPreferences()));
            node.set("exclusions", strings(context.getExclusions()));
            node.put("resultLimit", context.getResultLimit());
            if (context.getParentContextHandle() != null) node.put("parentContextHandle", context.getParentContextHandle().asBase64Url());
            return objectMapper.writeValueAsBytes(node);
        } catch (Exception exception) {
            throw new IllegalStateException("context encoding is unavailable", exception);
        }
    }

    @Override
    public RecommendationContext decode(byte[] payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            requireObject(node);
            rejectUnknown(node, Set.of("authorizedScope", "profileVersion", "baselineCriteria", "constraints", "preferences", "exclusions", "resultLimit", "parentContextHandle"));
            JsonNode scope = node.get("authorizedScope");
            requireObject(scope);
            rejectUnknown(scope, Set.of("mode", "contentVersion", "exactSubjects"));
            String contentVersion = requiredText(scope, "contentVersion");
            AuthorizedSubjectScope authorizedScope;
            AuthorizedSubjectScope.ScopeMode mode = enumValue(AuthorizedSubjectScope.ScopeMode.class, scope, "mode");
            if (mode == AuthorizedSubjectScope.ScopeMode.ALL_PUBLISHED_CANDIDATES) authorizedScope = AuthorizedSubjectScope.allPublishedCandidates(contentVersion);
            else authorizedScope = AuthorizedSubjectScope.exactSubjects(decodeSubjects(requiredArray(scope, "exactSubjects")), contentVersion);
            String parent = optionalText(node, "parentContextHandle");
            return new RecommendationContext(authorizedScope, requiredText(node, "profileVersion"),
                    stringSet(requiredArray(node, "baselineCriteria")), stringSet(requiredArray(node, "constraints")),
                    stringSet(requiredArray(node, "preferences")), stringSet(requiredArray(node, "exclusions")),
                    requiredInt(node, "resultLimit"), parent == null ? null : ContextHandle.fromBase64Url(parent));
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("context payload is invalid", exception); }
    }

    private ArrayNode strings(Set<String> values) { ArrayNode array = objectMapper.createArrayNode(); values.stream().sorted().forEach(array::add); return array; }
    private static List<SubjectReference> decodeSubjects(JsonNode array) {
        List<SubjectReference> values = new ArrayList<>();
        for (JsonNode item : array) {
            requireObject(item);
            values.add(new SubjectReference(enumValue(SemanticRoutingTypes.SubjectType.class, item, "subjectType"), requiredText(item, "subjectId"), enumValue(SemanticRoutingTypes.SubjectResolutionSource.class, item, "resolutionSource"), optionalText(item, "contentVersion")));
        }
        return List.copyOf(values);
    }
    private static Set<String> stringSet(JsonNode array) { Set<String> values = new LinkedHashSet<>(); for (JsonNode value : array) { if (!value.isTextual() || value.asText().isBlank() || !values.add(value.asText())) throw new IllegalArgumentException("context payload is invalid"); } return Set.copyOf(values); }
    private static JsonNode requiredArray(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || !value.isArray()) throw new IllegalArgumentException("context payload is invalid"); return value; }
    private static int requiredInt(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || !value.canConvertToInt()) throw new IllegalArgumentException("context payload is invalid"); return value.intValue(); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) { return Enum.valueOf(type, requiredText(node, field)); }
    private static String requiredText(JsonNode node, String field) { String value = optionalText(node, field); if (value == null) throw new IllegalArgumentException("context payload is invalid"); return value; }
    private static String optionalText(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || value.isNull()) return null; if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("context payload is invalid"); return value.asText().trim(); }
    private static void requireObject(JsonNode node) { if (node == null || !node.isObject()) throw new IllegalArgumentException("context payload is invalid"); }
    private static void rejectUnknown(JsonNode node, Set<String> allowed) { node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new IllegalArgumentException("context payload is invalid"); }); }
}
