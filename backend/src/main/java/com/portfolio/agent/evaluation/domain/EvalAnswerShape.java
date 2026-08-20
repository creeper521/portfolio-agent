package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.evaluation.domain.AnswerSectionMapping;
import com.portfolio.agent.evaluation.domain.ConversationAnswerBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Structural snapshot of an answer that never retains answer content.
 *
 * <p>Content is normalized and counted only inside the factory stack; the
 * resulting value holds counts, not text, hashes, ids or prompts.</p>
 */
public final class EvalAnswerShape {

    private final int blockCount;
    private final int characterCount;
    private final int distinctClaimCount;
    private final int distinctEvidenceCount;
    private final int repeatedClaimReferenceCount;
    private final int repeatedEvidenceReferenceCount;
    private final int repeatedContentCount;
    private final int repeatedSourceScopeCount;
    private final int semanticSectionCount;
    private final int typedSectionCount;
    private final int untypedBlockCount;
    private final boolean sectionOrderValid;
    private final boolean summaryPresent;
    private final boolean directAnswerPresent;

    private EvalAnswerShape(
            int blockCount,
            int characterCount,
            int distinctClaimCount,
            int distinctEvidenceCount,
            int repeatedClaimReferenceCount,
            int repeatedEvidenceReferenceCount,
            int repeatedContentCount,
            int repeatedSourceScopeCount,
            int semanticSectionCount,
            int typedSectionCount,
            int untypedBlockCount,
            boolean sectionOrderValid,
            boolean summaryPresent,
            boolean directAnswerPresent) {
        this.blockCount = blockCount;
        this.characterCount = characterCount;
        this.distinctClaimCount = distinctClaimCount;
        this.distinctEvidenceCount = distinctEvidenceCount;
        this.repeatedClaimReferenceCount = repeatedClaimReferenceCount;
        this.repeatedEvidenceReferenceCount = repeatedEvidenceReferenceCount;
        this.repeatedContentCount = repeatedContentCount;
        this.repeatedSourceScopeCount = repeatedSourceScopeCount;
        this.semanticSectionCount = semanticSectionCount;
        this.typedSectionCount = typedSectionCount;
        this.untypedBlockCount = untypedBlockCount;
        this.sectionOrderValid = sectionOrderValid;
        this.summaryPresent = summaryPresent;
        this.directAnswerPresent = directAnswerPresent;
    }

    public static EvalAnswerShape empty() {
        return new EvalAnswerShape(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                true, false, false);
    }

    public static EvalAnswerShape from(List<ConversationAnswerBlock> blocks) {
        return from(blocks, null);
    }

    public static EvalAnswerShape from(
            List<ConversationAnswerBlock> blocks,
            String summary) {
        Objects.requireNonNull(blocks, "blocks");
        if (blocks.isEmpty()) {
            boolean summaryPresent = hasText(summary);
            return new EvalAnswerShape(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    true, summaryPresent, summaryPresent);
        }
        int characterCount = 0;
        int semanticSectionCount = 0;
        int typedSectionCount = 0;
        int untypedBlockCount = 0;
        int repeatedContent = 0;
        int repeatedSourceScope = 0;
        List<String> allClaims = new ArrayList<>();
        List<String> allEvidence = new ArrayList<>();
        Set<String> normalizedContents = new HashSet<>();
        Set<String> sourceScopes = new HashSet<>();
        List<AnswerSectionType> typedSections = new ArrayList<>();
        boolean directAnswerPresent = false;
        for (int index = 0; index < blocks.size(); index++) {
            ConversationAnswerBlock block = blocks.get(index);
            String normalized = block.getContent() == null
                    ? "" : block.getContent().trim();
            characterCount += normalized.length();
            if (block.getSectionType() != null) {
                typedSectionCount++;
                typedSections.add(block.getSectionType());
                if (!normalized.isEmpty()) {
                    semanticSectionCount++;
                }
            } else {
                untypedBlockCount++;
            }
            if (index == 0 && !normalized.isEmpty()) {
                directAnswerPresent = true;
            }
            if (!normalized.isEmpty() && !normalizedContents.add(normalized)) {
                repeatedContent++;
            }
            if (block.getSourceScope() != null
                    && !sourceScopes.add(block.getSourceScope().name())) {
                repeatedSourceScope++;
            }
            allClaims.addAll(block.getClaimIds());
            allEvidence.addAll(block.getEvidenceIds());
        }
        int distinctClaims = (int) new HashSet<>(allClaims).size();
        int distinctEvidence = (int) new HashSet<>(allEvidence).size();
        boolean summaryPresent = hasText(summary);
        if (!directAnswerPresent && summaryPresent) {
            directAnswerPresent = true;
        }
        return new EvalAnswerShape(
                blocks.size(),
                characterCount,
                distinctClaims,
                distinctEvidence,
                allClaims.size() - distinctClaims,
                allEvidence.size() - distinctEvidence,
                repeatedContent,
                repeatedSourceScope,
                semanticSectionCount,
                typedSectionCount,
                untypedBlockCount,
                sectionOrderValid(typedSections),
                summaryPresent,
                directAnswerPresent);
    }

    private static boolean sectionOrderValid(List<AnswerSectionType> typedSections) {
        int previousRank = -1;
        for (AnswerSectionType type : typedSections) {
            int rank = AnswerSectionMapping.authoritativeOrder().indexOf(type);
            if (rank < 0) {
                return false;
            }
            if (rank < previousRank) {
                return false;
            }
            previousRank = rank;
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public int getBlockCount() { return blockCount; }
    public int getCharacterCount() { return characterCount; }
    public int getDistinctClaimCount() { return distinctClaimCount; }
    public int getDistinctEvidenceCount() { return distinctEvidenceCount; }
    public int getRepeatedClaimReferenceCount() { return repeatedClaimReferenceCount; }
    public int getRepeatedEvidenceReferenceCount() { return repeatedEvidenceReferenceCount; }
    public int getRepeatedContentCount() { return repeatedContentCount; }
    public int getRepeatedSourceScopeCount() { return repeatedSourceScopeCount; }
    public int getSemanticSectionCount() { return semanticSectionCount; }
    public int getTypedSectionCount() { return typedSectionCount; }
    public int getUntypedBlockCount() { return untypedBlockCount; }
    public boolean isSectionOrderValid() { return sectionOrderValid; }
    public boolean isSummaryPresent() { return summaryPresent; }
    public boolean isDirectAnswerPresent() { return directAnswerPresent; }

    @Override
    public String toString() {
        return "EvalAnswerShape{blockCount=" + blockCount
                + ", characterCount=" + characterCount
                + ", distinctClaimCount=" + distinctClaimCount
                + ", distinctEvidenceCount=" + distinctEvidenceCount
                + ", repeatedClaimReferenceCount=" + repeatedClaimReferenceCount
                + ", repeatedEvidenceReferenceCount=" + repeatedEvidenceReferenceCount
                + ", repeatedContentCount=" + repeatedContentCount
                + ", repeatedSourceScopeCount=" + repeatedSourceScopeCount
                + ", semanticSectionCount=" + semanticSectionCount
                + ", typedSectionCount=" + typedSectionCount
                + ", untypedBlockCount=" + untypedBlockCount
                + ", sectionOrderValid=" + sectionOrderValid
                + ", summaryPresent=" + summaryPresent
                + ", directAnswerPresent=" + directAnswerPresent + '}';
    }
}
