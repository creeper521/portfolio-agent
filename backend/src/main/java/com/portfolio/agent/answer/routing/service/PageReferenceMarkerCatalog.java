package com.portfolio.agent.answer.routing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.TextAnchor;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable application-versioned directory of complete PAGE_HINT phrases, not portfolio content. */
public final class PageReferenceMarkerCatalog {

    private static final String VERSION = "page-reference-markers.v1";
    private static final int EXPECTED_MARKER_COUNT = 10;

    private final Set<Marker> markers;

    private PageReferenceMarkerCatalog(Set<Marker> markers) {
        this.markers = Set.copyOf(markers);
    }

    public static PageReferenceMarkerCatalog load(InputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("marker configuration is required");
        }
        try (InputStream input = stream) {
            JsonNode root = new ObjectMapper().readTree(input);
            if (root == null || !root.isObject() || root.size() != 2
                    || !VERSION.equals(root.path("version").asText()) || !root.path("markers").isArray()) {
                throw new IllegalArgumentException("marker configuration is invalid");
            }
            Set<Marker> markers = new LinkedHashSet<>();
            for (JsonNode node : root.path("markers")) {
                if (!node.isObject() || node.size() != 2) {
                    throw new IllegalArgumentException("marker entry is invalid");
                }
                SubjectType type = SubjectType.valueOf(node.path("subjectType").asText());
                String phrase = normalize(node.path("phrase").asText());
                if (phrase.isEmpty() || phrase.length() < 3 || isBarePronoun(phrase)
                        || !markers.add(new Marker(type, phrase))) {
                    throw new IllegalArgumentException("marker entry is not allowed");
                }
            }
            if (markers.size() != EXPECTED_MARKER_COUNT) {
                throw new IllegalArgumentException("marker configuration count is invalid");
            }
            return new PageReferenceMarkerCatalog(markers);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("marker configuration is invalid", exception);
        }
    }

    public boolean supports(TextAnchor anchor, String currentInput, SubjectType subjectType) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(subjectType, "subjectType");
        TextAnchor.TextSpan span;
        try {
            span = anchor.resolveIn(currentInput);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return markers.contains(new Marker(subjectType, normalize(span.getText())));
    }

    private static boolean isBarePronoun(String phrase) {
        return phrase.equals("它") || phrase.equals("这个") || phrase.equals("那个")
                || phrase.equals("it") || phrase.equals("this") || phrase.equals("that");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class Marker {
        private final SubjectType subjectType;
        private final String phrase;

        private Marker(SubjectType subjectType, String phrase) {
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
            this.phrase = Objects.requireNonNull(phrase, "phrase");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Marker that)) {
                return false;
            }
            return subjectType == that.subjectType && phrase.equals(that.phrase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjectType, phrase);
        }
    }
}
