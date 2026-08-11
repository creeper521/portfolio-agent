package com.portfolio.agent.answer.routing.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit renderable-result variants; generic maps are intentionally forbidden. */
public sealed interface TaskResultPayload
        permits TaskResultPayload.SectionResultPayload,
        TaskResultPayload.RecommendationResultPayload,
        TaskResultPayload.SynthesisResultPayload {

    final class SectionResultPayload implements TaskResultPayload {

        private final List<String> blocks;
        private final String summary;

        public SectionResultPayload(List<String> blocks, String summary) {
            this.blocks = copyBlocks(blocks, "blocks");
            this.summary = optionalText(summary);
        }

        public List<String> getBlocks() {
            return blocks;
        }

        public String getSummary() {
            return summary;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SectionResultPayload that)) {
                return false;
            }
            return Objects.equals(blocks, that.blocks) && Objects.equals(summary, that.summary);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blocks, summary);
        }

        @Override
        public String toString() {
            return "SectionResultPayload{blockCount=" + blocks.size()
                    + ", hasSummary=" + (summary != null) + '}';
        }
    }

    final class RecommendationResultPayload implements TaskResultPayload {

        private final String recommendation;
        private final List<RecommendationItem> items;
        private final List<String> supportingBlocks;

        public RecommendationResultPayload(String recommendation, List<String> supportingBlocks) {
            this.recommendation = requireText(recommendation, "recommendation");
            this.items = List.of();
            this.supportingBlocks = copyBlocks(supportingBlocks, "supportingBlocks");
        }

        public RecommendationResultPayload(
                List<RecommendationItem> items,
                List<String> supportingBlocks) {
            this.items = copyItems(items);
            this.recommendation = this.items.stream()
                    .map(RecommendationItem::getTitle)
                    .reduce((left, right) -> left + "; " + right)
                    .orElseThrow(() -> new IllegalArgumentException("items must not be empty"));
            this.supportingBlocks = copyBlocks(supportingBlocks, "supportingBlocks");
        }

        public String getRecommendation() {
            return recommendation;
        }

        public List<RecommendationItem> getItems() {
            return items;
        }

        public List<String> getSupportingBlocks() {
            return supportingBlocks;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RecommendationResultPayload that)) {
                return false;
            }
            return Objects.equals(recommendation, that.recommendation)
                    && Objects.equals(items, that.items)
                    && Objects.equals(supportingBlocks, that.supportingBlocks);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recommendation, items, supportingBlocks);
        }

        @Override
        public String toString() {
            return "RecommendationResultPayload{itemCount=" + items.size()
                    + ", supportingBlockCount=" + supportingBlocks.size() + '}';
        }
    }

    /** Typed public recommendation item retained per semantic task. */
    final class RecommendationItem {

        private final String portfolioId;
        private final String title;
        private final String route;
        private final List<String> matchReasons;
        private final List<String> evidenceIds;

        public RecommendationItem(
                String portfolioId,
                String title,
                String route,
                List<String> matchReasons,
                List<String> evidenceIds) {
            this.portfolioId = requireText(portfolioId, "portfolioId");
            this.title = requireText(title, "title");
            this.route = requireText(route, "route");
            this.matchReasons = copyTextList(matchReasons, "matchReasons");
            this.evidenceIds = copyTextList(evidenceIds, "evidenceIds");
        }

        public String getPortfolioId() { return portfolioId; }
        public String getTitle() { return title; }
        public String getRoute() { return route; }
        public List<String> getMatchReasons() { return matchReasons; }
        public List<String> getEvidenceIds() { return evidenceIds; }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RecommendationItem that)) {
                return false;
            }
            return Objects.equals(portfolioId, that.portfolioId)
                    && Objects.equals(title, that.title)
                    && Objects.equals(route, that.route)
                    && Objects.equals(matchReasons, that.matchReasons)
                    && Objects.equals(evidenceIds, that.evidenceIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(portfolioId, title, route, matchReasons, evidenceIds);
        }

        @Override
        public String toString() {
            return "RecommendationItem{hasPortfolioId=true, hasRoute=true, reasonCount="
                    + matchReasons.size() + ", evidenceCount=" + evidenceIds.size() + '}';
        }
    }

    final class SynthesisResultPayload implements TaskResultPayload {

        private final List<String> blocks;
        private final TaskResultProvenance provenance;

        public SynthesisResultPayload(List<String> blocks, TaskResultProvenance provenance) {
            this.blocks = copyBlocks(blocks, "blocks");
            this.provenance = Objects.requireNonNull(provenance, "provenance");
            if (provenance.getDerivationType() != TaskResultProvenance.DerivationType.SYNTHESIZED) {
                throw new IllegalArgumentException("synthesis payload requires synthesized provenance");
            }
        }

        public List<String> getBlocks() {
            return blocks;
        }

        public TaskResultProvenance getProvenance() {
            return provenance;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SynthesisResultPayload that)) {
                return false;
            }
            return Objects.equals(blocks, that.blocks) && Objects.equals(provenance, that.provenance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blocks, provenance);
        }

        @Override
        public String toString() {
            return "SynthesisResultPayload{blockCount=" + blocks.size() + '}';
        }
    }

    private static List<String> copyBlocks(List<String> blocks, String name) {
        Objects.requireNonNull(blocks, name);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        List<String> copied = new ArrayList<>();
        for (String block : blocks) {
            copied.add(requireText(block, name));
        }
        return List.copyOf(copied);
    }

    private static List<RecommendationItem> copyItems(
            List<RecommendationItem> items) {
        List<RecommendationItem> copied = List.copyOf(Objects.requireNonNull(items, "items"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        return copied;
    }

    private static List<String> copyTextList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copied = new ArrayList<>();
        for (String value : values) {
            copied.add(requireText(value, name));
        }
        return List.copyOf(copied);
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String name) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }
}
