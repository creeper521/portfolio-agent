package com.portfolio.agent.turn.capability.portfolio.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict atomic compiler for optional Fact wording. */
public final class PortfolioFactExpressionCompiler {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PresentationPolicy policy;
    public PortfolioFactExpressionCompiler(PresentationPolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }
    public PortfolioPresentation compile(
            PortfolioSemanticResult.Fact result,
            PortfolioPresentation canonical,
            String json) {
        try {
            JsonNode root = mapper.readTree(json);
            fields(root, Set.of("sections", "caveats"));
            JsonNode sectionsNode = root.path("sections");
            JsonNode caveatsNode = root.path("caveats");
            if (!sectionsNode.isArray() || sectionsNode.isEmpty()
                    || sectionsNode.size() > policy.getMaximumSections() || !caveatsNode.isArray()) {
                throw new IllegalArgumentException("expression shape is invalid");
            }
            Set<String> caveats = new HashSet<>();
            caveatsNode.forEach(value -> caveats.add(value.asText()));
            if (!caveats.containsAll(result.getOmissions())) {
                throw new IllegalArgumentException("required caveat was removed");
            }
            Map<String, PublicSourceReferenceValue> sources = new LinkedHashMap<>();
            canonical.getSections().forEach(section -> section.getSources().forEach(
                    source -> sources.putIfAbsent(source.getReferenceKey(), source)));
            List<PortfolioPresentation.Section> sections = new ArrayList<>();
            int characters = 0;
            for (JsonNode section : sectionsNode) {
                fields(section, Set.of("sectionType", "title", "content", "publicSourceKeys"));
                String title = text(section, "title");
                String content = text(section, "content");
                characters += title.length() + content.length();
                if (characters > policy.getMaximumCharacters()) {
                    throw new IllegalArgumentException("expression exceeds character bound");
                }
                JsonNode keys = section.path("publicSourceKeys");
                if (!keys.isArray() || keys.isEmpty()) {
                    throw new IllegalArgumentException("expression sources are required");
                }
                List<PublicSourceReferenceValue> resolved = new ArrayList<>();
                keys.forEach(value -> {
                    PublicSourceReferenceValue source = sources.get(value.asText());
                    if (source == null) throw new IllegalArgumentException("unknown public source key");
                    if (!resolved.contains(source)) resolved.add(source);
                });
                sections.add(new PortfolioPresentation.Section(
                        AnswerSectionType.valueOf(text(section, "sectionType")),
                        title, content, resolved));
            }
            return new PortfolioPresentation(canonical.getTitle(), sections);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("invalid fact expression JSON", failure);
        }
    }
    private void fields(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) throw new IllegalArgumentException("expression object is required");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(name -> {
            actual.add(name);
            if (!allowed.contains(name)) throw new IllegalArgumentException("unknown expression field");
        });
        if (!actual.equals(allowed)) throw new IllegalArgumentException("expression fields are incomplete");
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText().trim();
    }
}
