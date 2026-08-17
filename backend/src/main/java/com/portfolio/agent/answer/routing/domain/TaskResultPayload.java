package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Explicit renderable-result variants; generic maps are intentionally forbidden. */
public sealed interface TaskResultPayload
        permits TaskResultPayload.SectionResultPayload,
        TaskResultPayload.RecommendationResultPayload,
        TaskResultPayload.SynthesisResultPayload {

    final class SectionResultPayload implements TaskResultPayload {

        private final List<String> blocks;
        private final List<SectionBlock> sections;
        private final String summary;

        public SectionResultPayload(List<String> blocks, String summary) {
            this.blocks = copyBlocks(blocks, "blocks");
            this.sections = this.blocks.stream().map(SectionBlock::untyped).toList();
            this.summary = optionalText(summary);
        }

        private SectionResultPayload(List<SectionBlock> sections, String summary, boolean typed) {
            this.sections = copySections(sections);
            this.blocks = this.sections.stream().map(SectionBlock::getContent).toList();
            this.summary = optionalText(summary);
        }

        public static SectionResultPayload fromSections(List<SectionBlock> sections, String summary) {
            return new SectionResultPayload(sections, summary, true);
        }

        public List<String> getBlocks() {
            return blocks;
        }

        public List<SectionBlock> getSections() { return sections; }

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
            return Objects.equals(sections, that.sections) && Objects.equals(summary, that.summary);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sections, summary);
        }

        @Override
        public String toString() {
            return "SectionResultPayload{blockCount=" + blocks.size()
                    + ", hasSummary=" + (summary != null) + '}';
        }
    }

    final class SectionBlock {
        private final AnswerSectionType sectionType;
        private final String title;
        private final String content;
        private final List<String> claimIds;
        private final List<String> evidenceIds;
        private final List<PublicSourceReferenceValue> sourceReferences;

        public SectionBlock(
                AnswerSectionType sectionType, String title, String content,
                List<String> claimIds, List<String> evidenceIds) {
            this(sectionType, title, content, claimIds, evidenceIds, List.of());
        }

        public SectionBlock(
                AnswerSectionType sectionType, String title, String content,
                List<String> claimIds, List<String> evidenceIds,
                List<PublicSourceReferenceValue> sourceReferences) {
            this.sectionType = Objects.requireNonNull(sectionType, "sectionType");
            this.title = requireText(title, "title");
            this.content = requireText(content, "content");
            this.claimIds = copyTextList(claimIds, "claimIds");
            this.evidenceIds = copyTextList(evidenceIds, "evidenceIds");
            this.sourceReferences = List.copyOf(
                    Objects.requireNonNull(sourceReferences, "sourceReferences"));
        }

        private SectionBlock(String content) {
            this.sectionType = null;
            this.title = null;
            this.content = requireText(content, "content");
            this.claimIds = List.of();
            this.evidenceIds = List.of();
            this.sourceReferences = List.of();
        }

        static SectionBlock untyped(String content) { return new SectionBlock(content); }

        public AnswerSectionType getSectionType() { return sectionType; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public List<String> getClaimIds() { return claimIds; }
        public List<String> getEvidenceIds() { return evidenceIds; }
        public List<PublicSourceReferenceValue> getSourceReferences() { return sourceReferences; }
        public boolean isTyped() { return sectionType != null; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SectionBlock that)) return false;
            return sectionType == that.sectionType
                    && Objects.equals(title, that.title)
                    && Objects.equals(content, that.content)
                    && Objects.equals(claimIds, that.claimIds)
                    && Objects.equals(evidenceIds, that.evidenceIds)
                    && Objects.equals(sourceReferences, that.sourceReferences);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sectionType, title, content, claimIds, evidenceIds, sourceReferences);
        }
    }

    final class RecommendationResultPayload implements TaskResultPayload {

        private final String recommendation;
        private final List<RecommendationItem> items;
        private final List<String> supportingBlocks;
        private final RecommendationProjection projection;

        public RecommendationResultPayload(String recommendation, List<String> supportingBlocks) {
            this.recommendation = requireText(recommendation, "recommendation");
            this.items = List.of();
            this.supportingBlocks = copyBlocks(supportingBlocks, "supportingBlocks");
            this.projection = null;
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
            this.projection = null;
        }

        public RecommendationResultPayload(
                RecommendationProjection projection,
                List<String> supportingBlocks) {
            this.projection = Objects.requireNonNull(projection, "projection");
            this.items = projection.getItems();
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

        public RecommendationProjection getProjection() {
            return projection;
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
                    && Objects.equals(supportingBlocks, that.supportingBlocks)
                    && Objects.equals(projection, that.projection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recommendation, items, supportingBlocks, projection);
        }

        @Override
        public String toString() {
            return "RecommendationResultPayload{itemCount=" + items.size()
                    + ", supportingBlockCount=" + supportingBlocks.size() + '}';
        }
    }

    /** Complete public recommendation data retained for the single-result legacy projection. */
    final class RecommendationProjection {

        public enum CandidateScope { ALL_PUBLISHED_PROJECTS, EXPLICIT_PROJECT_SET }

        private final String recommendationBatchId;
        private final String contentVersion;
        private final String careerTrack;
        private final String audienceRole;
        private final Set<String> capabilityCodes;
        private final int requestedSize;
        private final int actualSize;
        private final CandidateScope candidateScope;
        private final List<String> selectedPortfolioIds;
        private final List<RecommendationItem> items;
        private final List<String> satisfiedConstraints;
        private final List<String> unsatisfiedConstraints;
        private final List<String> reasonCodes;

        public RecommendationProjection(
                String recommendationBatchId,
                String contentVersion,
                String careerTrack,
                String audienceRole,
                Set<String> capabilityCodes,
                int requestedSize,
                List<String> selectedPortfolioIds,
                List<RecommendationItem> items,
                List<String> satisfiedConstraints,
                List<String> unsatisfiedConstraints) {
            this(recommendationBatchId, contentVersion, careerTrack, audienceRole, capabilityCodes,
                    requestedSize, items.size(), CandidateScope.EXPLICIT_PROJECT_SET,
                    selectedPortfolioIds, items, satisfiedConstraints, unsatisfiedConstraints,
                    items.size() < requestedSize ? List.of("INSUFFICIENT_ELIGIBLE_PROJECTS") : List.of());
        }

        public RecommendationProjection(
                String recommendationBatchId, String contentVersion, String careerTrack, String audienceRole,
                Set<String> capabilityCodes, int requestedSize, int actualSize, CandidateScope candidateScope,
                List<String> selectedPortfolioIds, List<RecommendationItem> items,
                List<String> satisfiedConstraints, List<String> unsatisfiedConstraints, List<String> reasonCodes) {
            this.recommendationBatchId = requireText(
                    recommendationBatchId, "recommendationBatchId");
            this.contentVersion = requireText(contentVersion, "contentVersion");
            this.careerTrack = optionalText(careerTrack);
            this.audienceRole = requireText(audienceRole, "audienceRole");
            this.capabilityCodes = Set.copyOf(
                    Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
            if (requestedSize < 1 || requestedSize > 5) {
                throw new IllegalArgumentException("requestedSize must be between 1 and 5");
            }
            this.requestedSize = requestedSize;
            this.selectedPortfolioIds = copyTextList(
                    selectedPortfolioIds, "selectedPortfolioIds");
            this.items = copyItems(items);
            if (actualSize != this.items.size() || actualSize != this.selectedPortfolioIds.size()) {
                throw new IllegalArgumentException("actualSize must match items and selectedPortfolioIds");
            }
            if (new java.util.LinkedHashSet<>(this.selectedPortfolioIds).size() != this.selectedPortfolioIds.size()) {
                throw new IllegalArgumentException("selectedPortfolioIds must be distinct");
            }
            this.actualSize = actualSize;
            this.candidateScope = Objects.requireNonNull(candidateScope, "candidateScope");
            this.satisfiedConstraints = copyTextList(
                    satisfiedConstraints, "satisfiedConstraints");
            this.unsatisfiedConstraints = copyTextList(
                    unsatisfiedConstraints, "unsatisfiedConstraints");
            this.reasonCodes = copyTextList(reasonCodes, "reasonCodes");
            if (actualSize == requestedSize && !this.reasonCodes.isEmpty()) {
                throw new IllegalArgumentException("exact recommendation must not carry reasonCodes");
            }
            if (actualSize < requestedSize && this.reasonCodes.isEmpty()) {
                throw new IllegalArgumentException("partial recommendation requires reasonCodes");
            }
        }

        public String getRecommendationBatchId() { return recommendationBatchId; }
        public String getContentVersion() { return contentVersion; }
        public String getCareerTrack() { return careerTrack; }
        public String getAudienceRole() { return audienceRole; }
        public Set<String> getCapabilityCodes() { return capabilityCodes; }
        public int getRequestedSize() { return requestedSize; }
        public int getActualSize() { return actualSize; }
        public CandidateScope getCandidateScope() { return candidateScope; }
        public List<String> getSelectedPortfolioIds() { return selectedPortfolioIds; }
        public List<RecommendationItem> getItems() { return items; }
        public List<String> getSatisfiedConstraints() { return satisfiedConstraints; }
        public List<String> getUnsatisfiedConstraints() { return unsatisfiedConstraints; }
        public List<String> getReasonCodes() { return reasonCodes; }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RecommendationProjection that)) {
                return false;
            }
            return requestedSize == that.requestedSize
                    && Objects.equals(recommendationBatchId, that.recommendationBatchId)
                    && Objects.equals(contentVersion, that.contentVersion)
                    && Objects.equals(careerTrack, that.careerTrack)
                    && Objects.equals(audienceRole, that.audienceRole)
                    && Objects.equals(capabilityCodes, that.capabilityCodes)
                    && Objects.equals(selectedPortfolioIds, that.selectedPortfolioIds)
                    && Objects.equals(items, that.items)
                    && Objects.equals(satisfiedConstraints, that.satisfiedConstraints)
                    && Objects.equals(unsatisfiedConstraints, that.unsatisfiedConstraints);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recommendationBatchId, contentVersion, careerTrack,
                    audienceRole, capabilityCodes, requestedSize, selectedPortfolioIds,
                    items, satisfiedConstraints, unsatisfiedConstraints);
        }

        @Override
        public String toString() {
            return "RecommendationProjection{itemCount=" + items.size()
                    + ", capabilityCount=" + capabilityCodes.size()
                    + ", requestedSize=" + requestedSize + '}';
        }
    }

    /** Typed public recommendation item retained per semantic task. */
    final class RecommendationItem {

        private final String portfolioId;
        private final String title;
        private final String route;
        private final List<String> matchReasons;
        private final List<String> evidenceIds;
        private final List<PublicSourceReferenceValue> sourceReferences;

        public RecommendationItem(
                String portfolioId,
                String title,
                String route,
                List<String> matchReasons,
                List<String> evidenceIds) {
            this(portfolioId, title, route, matchReasons, evidenceIds, List.of());
        }

        public RecommendationItem(
                String portfolioId,
                String title,
                String route,
                List<String> matchReasons,
                List<String> evidenceIds,
                List<PublicSourceReferenceValue> sourceReferences) {
            this.portfolioId = requireText(portfolioId, "portfolioId");
            this.title = requireText(title, "title");
            this.route = requireText(route, "route");
            this.matchReasons = copyTextList(matchReasons, "matchReasons");
            this.evidenceIds = copyTextList(evidenceIds, "evidenceIds");
            this.sourceReferences = List.copyOf(
                    Objects.requireNonNull(sourceReferences, "sourceReferences"));
        }

        public String getPortfolioId() { return portfolioId; }
        public String getTitle() { return title; }
        public String getRoute() { return route; }
        public List<String> getMatchReasons() { return matchReasons; }
        public List<String> getEvidenceIds() { return evidenceIds; }
        public List<PublicSourceReferenceValue> getSourceReferences() { return sourceReferences; }

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
                    && Objects.equals(evidenceIds, that.evidenceIds)
                    && Objects.equals(sourceReferences, that.sourceReferences);
        }

        @Override
        public int hashCode() {
            return Objects.hash(portfolioId, title, route, matchReasons, evidenceIds, sourceReferences);
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

    private static List<SectionBlock> copySections(List<SectionBlock> sections) {
        List<SectionBlock> copied = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("sections must not be empty");
        }
        return copied;
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
