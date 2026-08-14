package com.portfolio.agent.answer.context.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.OrderedResultSelection;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.SubjectOrderKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RecommendationContextV2Codec implements ConversationContextCodec<RecommendationContext> {
    public static final String SCHEMA_VERSION = "p5-recommendation-v2";
    private final ObjectMapper mapper = new ObjectMapper();
    @Override public ConversationContextType getContextType() { return ConversationContextType.RECOMMENDATION; }
    @Override public String getSchemaVersion() { return SCHEMA_VERSION; }
    @Override public byte[] encode(RecommendationContext context) {
        try {
            ObjectNode node = (ObjectNode) mapper.readTree(new RecommendationContextCodec().encode(context));
            if (context.getRecommendationBatchId() != null) node.put("recommendationBatchId", context.getRecommendationBatchId());
            if (context.getSelectedResults() != null) {
                ObjectNode selected = node.putObject("selectedResults"); selected.put("orderKind", context.getSelectedResults().getOrderKind().name());
                ArrayNode items = selected.putArray("items");
                for (OrderedResultSelection.Item item : context.getSelectedResults().getItems()) {
                    ObjectNode value = items.addObject(); value.put("position", item.getPosition()); value.put("resultItemId", item.getResultItemId()); value.put("portfolioId", item.getPortfolioId()); value.put("subjectType", item.getSubjectType().name());
                }
            }
            return mapper.writeValueAsBytes(node);
        } catch (Exception exception) { throw new IllegalStateException("context encoding is unavailable", exception); }
    }
    @Override public RecommendationContext decode(byte[] payload) {
        try {
            ObjectNode node = (ObjectNode) mapper.readTree(payload);
            JsonNode selectedNode = node.remove("selectedResults"); String batch = node.has("recommendationBatchId") ? node.remove("recommendationBatchId").asText() : null;
            RecommendationContext base = new RecommendationContextCodec().decode(mapper.writeValueAsBytes(node));
            OrderedResultSelection selected = selectedNode == null || selectedNode.isNull() ? null : selected(selectedNode);
            return new RecommendationContext(base.getAuthorizedScope(), base.getProfileVersion(), base.getBaselineCriteria(), base.getConstraints(), base.getPreferences(), base.getExclusions(), base.getResultLimit(), base.getParentContextHandle(), selected, batch);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("context payload is invalid", exception); }
    }
    private static OrderedResultSelection selected(JsonNode node) {
        if (!node.isObject() || !node.has("orderKind") || !node.has("items") || !node.get("items").isArray()) throw new IllegalArgumentException("context payload is invalid");
        SubjectOrderKind kind = SubjectOrderKind.valueOf(node.get("orderKind").asText()); List<OrderedResultSelection.Item> items = new ArrayList<>();
        for (JsonNode item : node.get("items")) items.add(new OrderedResultSelection.Item(
                item.get("position").asInt(), item.get("resultItemId").asText(),
                item.get("portfolioId").asText(), item.has("subjectType")
                ? com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.valueOf(
                        item.get("subjectType").asText())
                : com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.PROJECT));
        return new OrderedResultSelection(kind, items);
    }
}
