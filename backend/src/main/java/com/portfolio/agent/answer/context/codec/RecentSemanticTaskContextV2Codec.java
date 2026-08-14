package com.portfolio.agent.answer.context.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.OrderedSubjectSelection;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.SubjectOrderKind;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reader for the P5 recent context shape. The registry intentionally keeps v1 as its writer. */
public final class RecentSemanticTaskContextV2Codec implements ConversationContextCodec<RecentSemanticTaskContext> {
    public static final String SCHEMA_VERSION = "p5-recent-v2";
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public ConversationContextType getContextType() { return ConversationContextType.RECENT_SEMANTIC_TASK; }
    @Override public String getSchemaVersion() { return SCHEMA_VERSION; }
    @Override public byte[] encode(RecentSemanticTaskContext context) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("taskType", context.getTaskType().name()); node.set("publicSubjects", subjects(context.getPublicSubjects()));
            if (context.getOrderedSelection() != null) node.set("orderedSelection", ordered(context.getOrderedSelection()));
            node.set("facets", strings(context.getFacets())); node.set("dimensions", strings(context.getDimensions()));
            node.put("contentVersion", context.getContentVersion()); node.put("sourceTaskId", context.getSourceTaskId());
            return mapper.writeValueAsBytes(node);
        } catch (Exception exception) { throw new IllegalStateException("context encoding is unavailable", exception); }
    }
    @Override public RecentSemanticTaskContext decode(byte[] payload) {
        try {
            JsonNode node = mapper.readTree(payload); requireObject(node);
            rejectUnknown(node, Set.of("taskType", "publicSubjects", "orderedSelection", "facets", "dimensions", "contentVersion", "sourceTaskId"));
            return new RecentSemanticTaskContext(enumValue(SemanticRoutingTypes.SemanticTaskType.class, node, "taskType"),
                    decodeSubjects(requiredArray(node, "publicSubjects")), stringSet(requiredArray(node, "facets")),
                    stringSet(requiredArray(node, "dimensions")), requiredText(node, "contentVersion"),
                    requiredText(node, "sourceTaskId"), node.has("orderedSelection") && !node.get("orderedSelection").isNull()
                            ? decodeOrdered(node.get("orderedSelection")) : null);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("context payload is invalid", exception); }
    }
    private ArrayNode subjects(List<SubjectReference> values) { ArrayNode array = mapper.createArrayNode(); for (SubjectReference subject : values) addSubject(array, subject); return array; }
    private ObjectNode ordered(OrderedSubjectSelection selection) { ObjectNode node = mapper.createObjectNode(); node.put("orderKind", selection.getOrderKind().name()); ArrayNode items = node.putArray("items"); for (OrderedSubjectSelection.Item item : selection.getItems()) { ObjectNode value = items.addObject(); value.put("position", item.getPosition()); ObjectNode subject = value.putObject("subject"); addSubject(subject, item.getSubject()); } return node; }
    private void addSubject(ArrayNode array, SubjectReference subject) { addSubject(array.addObject(), subject); }
    private void addSubject(ObjectNode node, SubjectReference subject) { node.put("subjectType", subject.getSubjectType().name()); node.put("subjectId", subject.getSubjectId()); node.put("resolutionSource", subject.getResolutionSource().name()); if (subject.getContentVersion() != null) node.put("contentVersion", subject.getContentVersion()); }
    private ArrayNode strings(Set<String> values) { ArrayNode array = mapper.createArrayNode(); values.stream().sorted().forEach(array::add); return array; }
    private static OrderedSubjectSelection decodeOrdered(JsonNode node) { requireObject(node); rejectUnknown(node, Set.of("orderKind", "items")); SubjectOrderKind kind = enumValue(SubjectOrderKind.class, node, "orderKind"); List<OrderedSubjectSelection.Item> items = new ArrayList<>(); for (JsonNode value : requiredArray(node, "items")) { requireObject(value); rejectUnknown(value, Set.of("position", "subject")); JsonNode subject = value.get("subject"); requireObject(subject); items.add(new OrderedSubjectSelection.Item(requiredInt(value, "position"), decodeSubject(subject))); } return new OrderedSubjectSelection(kind, items); }
    private static List<SubjectReference> decodeSubjects(JsonNode array) { List<SubjectReference> values = new ArrayList<>(); for (JsonNode item : array) { requireObject(item); values.add(decodeSubject(item)); } return List.copyOf(values); }
    private static SubjectReference decodeSubject(JsonNode item) { return new SubjectReference(enumValue(SemanticRoutingTypes.SubjectType.class, item, "subjectType"), requiredText(item, "subjectId"), enumValue(SemanticRoutingTypes.SubjectResolutionSource.class, item, "resolutionSource"), optionalText(item, "contentVersion")); }
    private static Set<String> stringSet(JsonNode array) { Set<String> values = new LinkedHashSet<>(); for (JsonNode value : array) { if (!value.isTextual() || value.asText().isBlank() || !values.add(value.asText())) throw new IllegalArgumentException("context payload is invalid"); } return Set.copyOf(values); }
    private static JsonNode requiredArray(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || !value.isArray()) throw new IllegalArgumentException("context payload is invalid"); return value; }
    private static int requiredInt(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || !value.canConvertToInt()) throw new IllegalArgumentException("context payload is invalid"); return value.intValue(); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) { return Enum.valueOf(type, requiredText(node, field)); }
    private static String requiredText(JsonNode node, String field) { String value = optionalText(node, field); if (value == null) throw new IllegalArgumentException("context payload is invalid"); return value; }
    private static String optionalText(JsonNode node, String field) { JsonNode value = node.get(field); if (value == null || value.isNull()) return null; if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("context payload is invalid"); return value.asText().trim(); }
    private static void requireObject(JsonNode node) { if (node == null || !node.isObject()) throw new IllegalArgumentException("context payload is invalid"); }
    private static void rejectUnknown(JsonNode node, Set<String> allowed) { node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new IllegalArgumentException("context payload is invalid"); }); }
}
