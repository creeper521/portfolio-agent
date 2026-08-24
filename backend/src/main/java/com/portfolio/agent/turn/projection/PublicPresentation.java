package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kind", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PublicPresentation.Sectioned.class, name = "SECTIONED"),
        @JsonSubTypes.Type(value = PublicPresentation.Recommendation.class, name = "RECOMMENDATION")
})
public sealed interface PublicPresentation
        permits PublicPresentation.Sectioned, PublicPresentation.Recommendation {
    Kind getKind();
    enum Kind { SECTIONED, RECOMMENDATION }

    final class Sectioned implements PublicPresentation {
        private final List<PublicSection> sections;
        public Sectioned(List<PublicSection> sections) {
            this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (this.sections.isEmpty()) throw new IllegalArgumentException("sections are required");
        }
        @Override public Kind getKind() { return Kind.SECTIONED; }
        public List<PublicSection> getSections() { return sections; }
    }

    final class Recommendation implements PublicPresentation {
        private final int requestedSize;
        private final int actualSize;
        private final List<Item> items;
        private final List<String> unsatisfiedConstraints;
        private final List<String> incompleteReasons;
        private final List<PublicSection> supportingSections;

        public Recommendation(
                int requestedSize, List<Item> items,
                List<String> unsatisfiedConstraints, List<String> incompleteReasons,
                List<PublicSection> supportingSections) {
            if (requestedSize < 1 || requestedSize > 5) {
                throw new IllegalArgumentException("requestedSize is invalid");
            }
            this.requestedSize = requestedSize;
            this.items = List.copyOf(Objects.requireNonNull(items, "items"));
            this.actualSize = this.items.size();
            if (actualSize < 1 || actualSize > requestedSize) {
                throw new IllegalArgumentException("recommendation item count is invalid");
            }
            this.unsatisfiedConstraints = texts(unsatisfiedConstraints, "unsatisfiedConstraints");
            this.incompleteReasons = texts(incompleteReasons, "incompleteReasons");
            this.supportingSections = List.copyOf(
                    Objects.requireNonNull(supportingSections, "supportingSections"));
            if (actualSize < requestedSize && this.incompleteReasons.isEmpty()) {
                throw new IllegalArgumentException("incomplete recommendation requires reasons");
            }
            if (actualSize == requestedSize && !this.incompleteReasons.isEmpty()) {
                throw new IllegalArgumentException(
                        "complete recommendation count cannot report size gaps");
            }
        }
        @Override public Kind getKind() { return Kind.RECOMMENDATION; }
        public int getRequestedSize() { return requestedSize; }
        public int getActualSize() { return actualSize; }
        public List<Item> getItems() { return items; }
        public List<String> getUnsatisfiedConstraints() { return unsatisfiedConstraints; }
        public List<String> getIncompleteReasons() { return incompleteReasons; }
        public List<PublicSection> getSupportingSections() { return supportingSections; }

        public static final class Item {
            private final String resultItemId;
            private final String label;
            private final String summary;
            private final String route;
            private final List<String> reasons;
            private final PublicSupport support;
            private final SuggestedAction discussionAction;
            public Item(
                    String resultItemId, String label, String summary, String route,
                    List<String> reasons, PublicSupport support,
                    SuggestedAction discussionAction) {
                this.resultItemId = text(resultItemId, "resultItemId");
                this.label = text(label, "label");
                this.summary = text(summary, "summary");
                this.route = new PublicSourceCatalog.Source(
                        "route-validator", null, "route-validator", null, route).getRoute();
                this.reasons = texts(reasons, "reasons");
                this.support = Objects.requireNonNull(support, "support");
                this.discussionAction = discussionAction;
            }
            public String getResultItemId() { return resultItemId; }
            public String getLabel() { return label; }
            public String getSummary() { return summary; }
            public String getRoute() { return route; }
            public List<String> getReasons() { return reasons; }
            public PublicSupport getSupport() { return support; }
            public SuggestedAction getDiscussionAction() {
                return discussionAction;
            }
        }

        private static List<String> texts(List<String> values, String name) {
            List<String> copied = List.copyOf(Objects.requireNonNull(values, name));
            if (copied.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(name + " contains blank values");
            }
            return copied;
        }
        private static String text(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value.trim();
        }
    }
}
