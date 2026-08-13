package com.portfolio.agent.answer.composition.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.draft.ComparisonExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.DraftSentence;
import com.portfolio.agent.answer.composition.domain.draft.DraftText;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.ModelExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.RecommendationExpressionDraft;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PortfolioExpressionDraftCodec {
    private static final int MAX_MODEL_CHARACTERS = 2_400;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public ModelExpressionDraft decode(String raw, MaterialKind expectedKind) {
        try {
            if (raw == null || raw.isBlank() || expectedKind == null) fail();
            JsonNode root = mapper.readTree(raw);
            if (root == null || !root.isObject()) fail();
            requireText(root, "schemaVersion");
            requireText(root, "materialKind");
            if (!ModelExpressionDraft.SCHEMA_VERSION.equals(root.get("schemaVersion").textValue())
                    || !expectedKind.name().equals(root.get("materialKind").textValue())) fail();
            ModelExpressionDraft draft = switch (expectedKind) {
                case FACT -> fact(root);
                case COMPARISON -> comparison(root);
                case RECOMMENDATION -> recommendation(root);
            };
            int characters = draft.allBodySentences().stream()
                    .mapToInt(sentence -> sentence.getText().length()).sum()
                    + draft.introductoryTexts().stream()
                            .mapToInt(value -> value.getText().length()).sum();
            if (characters > MAX_MODEL_CHARACTERS) fail();
            return draft;
        } catch (ExpressionDraftDecodingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExpressionDraftDecodingException(exception);
        }
    }

    private static FactExpressionDraft fact(JsonNode root) {
        requireFields(root, "schemaVersion", "materialKind", "summary", "sections");
        DraftText summary = root.get("summary").isNull() ? null : draftText(root.get("summary"));
        if (summary != null && summary.getText().length() > 300) fail();
        JsonNode sectionsNode = array(root, "sections", 1, 6);
        List<FactExpressionDraft.FactDraftSection> sections = new ArrayList<>();
        Set<String> sectionTypes = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (JsonNode sectionNode : sectionsNode) {
            requireFields(sectionNode, "sectionType", "sentences");
            requireText(sectionNode, "sectionType");
            String sectionName = sectionNode.get("sectionType").textValue();
            if (!sectionTypes.add(sectionName)) fail();
            AnswerSectionType sectionType = enumValue(AnswerSectionType.class, sectionName);
            JsonNode sentenceNodes = array(sectionNode, "sentences", 1, 4);
            List<DraftSentence> sentences = new ArrayList<>();
            for (JsonNode sentenceNode : sentenceNodes) {
                DraftSentence sentence = sentence(sentenceNode);
                if (!texts.add(sentence.getText())) fail();
                sentences.add(sentence);
            }
            sections.add(new FactExpressionDraft.FactDraftSection(sectionType, sentences));
        }
        if (summary != null && !texts.add(summary.getText())) fail();
        return new FactExpressionDraft(ModelExpressionDraft.SCHEMA_VERSION, summary, sections);
    }

    private static ComparisonExpressionDraft comparison(JsonNode root) {
        requireFields(root, "schemaVersion", "materialKind", "intro", "dimensions");
        DraftText intro = draftText(root.get("intro"));
        JsonNode dimensionsNode = array(root, "dimensions", 1, 16);
        List<ComparisonExpressionDraft.ComparisonDraftDimension> dimensions = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> texts = new HashSet<>();
        texts.add(intro.getText());
        for (JsonNode dimensionNode : dimensionsNode) {
            requireFields(dimensionNode, "dimensionKey", "subjects", "comparisonSentences");
            requireText(dimensionNode, "dimensionKey");
            String key = dimensionNode.get("dimensionKey").textValue();
            if (!keys.add(key)) fail();
            List<ComparisonExpressionDraft.ComparisonDraftSubject> subjects = new ArrayList<>();
            for (JsonNode subjectNode : array(dimensionNode, "subjects", 1, 16)) {
                requireFields(subjectNode, "subjectKey", "sentences");
                requireText(subjectNode, "subjectKey");
                List<DraftSentence> sentences = sentences(subjectNode.get("sentences"), 0, 4, texts);
                subjects.add(new ComparisonExpressionDraft.ComparisonDraftSubject(
                        subjectNode.get("subjectKey").textValue(), sentences));
            }
            List<DraftSentence> comparisonSentences = sentences(
                    dimensionNode.get("comparisonSentences"), 0, 4, texts);
            dimensions.add(new ComparisonExpressionDraft.ComparisonDraftDimension(
                    key, subjects, comparisonSentences));
        }
        return new ComparisonExpressionDraft(ModelExpressionDraft.SCHEMA_VERSION, intro, dimensions);
    }

    private static RecommendationExpressionDraft recommendation(JsonNode root) {
        requireFields(root, "schemaVersion", "materialKind", "intro", "items");
        DraftText intro = draftText(root.get("intro"));
        List<RecommendationExpressionDraft.RecommendationDraftItem> items = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> texts = new HashSet<>();
        texts.add(intro.getText());
        for (JsonNode itemNode : array(root, "items", 1, 16)) {
            requireFields(itemNode, "candidateKey", "sentences");
            requireText(itemNode, "candidateKey");
            String key = itemNode.get("candidateKey").textValue();
            if (!keys.add(key)) fail();
            items.add(new RecommendationExpressionDraft.RecommendationDraftItem(
                    key, sentences(itemNode.get("sentences"), 1, 4, texts)));
        }
        return new RecommendationExpressionDraft(ModelExpressionDraft.SCHEMA_VERSION, intro, items);
    }

    private static DraftText draftText(JsonNode node) {
        requireFields(node, "text", "supports");
        requireText(node, "text");
        return new DraftText(node.get("text").textValue(), supports(node.get("supports")));
    }

    private static DraftSentence sentence(JsonNode node) {
        requireFields(node, "text", "supports");
        requireText(node, "text");
        return new DraftSentence(node.get("text").textValue(), supports(node.get("supports")));
    }

    private static List<DraftSentence> sentences(JsonNode node, int minimum, int maximum,
            Set<String> texts) {
        if (node == null || !node.isArray() || node.size() < minimum || node.size() > maximum) fail();
        List<DraftSentence> values = new ArrayList<>();
        for (JsonNode item : node) {
            DraftSentence sentence = sentence(item);
            if (!texts.add(sentence.getText())) fail();
            values.add(sentence);
        }
        return values;
    }

    private static List<String> supports(JsonNode node) {
        if (node == null || !node.isArray() || node.size() < 1 || node.size() > 4) fail();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) fail();
            values.add(item.textValue());
        }
        return values;
    }

    private static JsonNode array(JsonNode parent, String name, int minimum, int maximum) {
        JsonNode value = parent.get(name);
        if (value == null || !value.isArray() || value.size() < minimum || value.size() > maximum) fail();
        return value;
    }

    private static void requireText(JsonNode node, String name) {
        if (!node.has(name) || !node.get(name).isTextual()) fail();
    }

    private static void requireFields(JsonNode node, String... names) {
        if (node == null || !node.isObject()) fail();
        Set<String> expected = Set.of(names);
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) if (!expected.contains(fields.next().getKey())) fail();
        for (String name : names) if (!node.has(name)) fail();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { fail(); return null; }
    }

    private static void fail() { throw new ExpressionDraftDecodingException(); }
}
