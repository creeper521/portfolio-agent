package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerEvidence;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKeywordIndex;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerRetrievalChunk;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerRetrievalCorpus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerSubjectType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BundlePortfolioRetrieverSubjectKindTest {

    @Test
    void recommendationExcludesCasesBeforeRankingAndCandidateConstruction() {
        RuntimeAnswerContent content = content(true);

        RetrievalAttemptResult result = adapter(content).retrieve(
                recommendationInvocation(),
                new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.KEYWORD),
                activeDeadline());

        assertThat(result.getCandidateSet().orElseThrow().getSubjects())
                .extracting(CandidateSubject::getSubjectId)
                .containsExactlyInAnyOrder("project-a", "project-b");
        assertThat(result.getCandidateSet().orElseThrow().getSubjects())
                .allSatisfy(subject -> assertThat(subject.getSubjectKind())
                        .isEqualTo(PortfolioSubjectKind.PROJECT));
    }

    @Test
    void caseDocumentsDoNotChangeEligibleProjectBm25Ordering() {
        List<String> baseline = projectOrder(content(false));
        List<String> withCasePerturbation = projectOrder(content(true));

        assertThat(baseline).containsExactly("project-b", "project-a");
        assertThat(withCasePerturbation).isEqualTo(baseline);
    }

    @Test
    void exactScopeRejectsSameIdWithDifferentSubjectKind() {
        RetrievalAttemptResult result = adapter(exactKindConflictContent()).retrieve(
                exactProjectInvocation(),
                new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.EXACT),
                activeDeadline());

        assertThat(result.getCandidateSet().orElseThrow().getSubjects()).isEmpty();
    }

    private List<String> projectOrder(RuntimeAnswerContent content) {
        RetrievalAttemptResult result = adapter(content).retrieve(
                recommendationInvocation(),
                new RetrievalRequest(CorpusBackend.BUNDLE, SearchStrategy.KEYWORD),
                activeDeadline());
        return result.getCandidateSet().orElseThrow().getSubjects().stream()
                .map(CandidateSubject::getSubjectId)
                .filter(subjectId -> subjectId.startsWith("project-"))
                .toList();
    }

    private BundlePortfolioRetrieverAdapter adapter(RuntimeAnswerContent content) {
        return new BundlePortfolioRetrieverAdapter(
                () -> content,
                controlledQuery -> {
                    throw new AssertionError("keyword retrieval must not embed");
                },
                false);
    }

    private PortfolioEvidenceInvocation recommendationInvocation() {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                AuthorizedSubjectScope.allPublished("public-1"),
                Set.of(PortfolioSubjectKind.PROJECT),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION),
                List.of(),
                "public-1",
                CorpusBackend.POSTGRESQL,
                SearchStrategy.HYBRID,
                CorpusBackend.BUNDLE,
                SearchStrategy.KEYWORD);
    }

    private PortfolioEvidenceInvocation exactProjectInvocation() {
        GoalSubjectReference reference = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT,
                "shared-id",
                GoalSubjectReference.Basis.SURFACE_HINT,
                null);
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.exact(List.of(reference), "public-1"),
                Set.of(PortfolioSubjectKind.PROJECT, PortfolioSubjectKind.CASE),
                List.of(PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION),
                List.of(),
                "public-1",
                CorpusBackend.BUNDLE,
                SearchStrategy.EXACT,
                null,
                null);
    }

    private RuntimeAnswerContent exactKindConflictContent() {
        AnswerKnowledge caseKnowledge = knowledge(
                AnswerSubjectType.CASE,
                "shared-id",
                "shared-id",
                "claim-shared",
                "evidence-shared");
        AnswerRetrievalChunk chunk = chunk(
                "chunk-shared", null, "shared-id", "claim-shared", "实现", 1);
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                1,
                1.0d,
                List.of(document("chunk-shared", 1, "实现")),
                Map.of("实现", 1));
        AnswerRetrievalCorpus corpus = new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of(),
                Map.of("chunk-shared", chunk),
                "test-model",
                "test-sha",
                2);
        return new RuntimeAnswerContent(
                "public-1", "hash-1", List.of(), List.of(caseKnowledge), corpus, List.of());
    }

    private RuntimeAnswerContent content(boolean includeCases) {
        List<AnswerKnowledge> projects = List.of(
                knowledge(AnswerSubjectType.PROJECT, "project-a", "project-a",
                        "claim-project-a", "evidence-project-a"),
                knowledge(AnswerSubjectType.PROJECT, "project-b", "project-b",
                        "claim-project-b", "evidence-project-b"));
        List<AnswerKnowledge> cases = new ArrayList<>();
        if (includeCases) {
            for (int index = 0; index < 20; index++) {
                cases.add(knowledge(
                        AnswerSubjectType.CASE,
                        "case-" + index,
                        "case-" + index,
                        "claim-case-" + index,
                        "evidence-case-" + index));
            }
        }

        Map<String, AnswerRetrievalChunk> chunks = new LinkedHashMap<>();
        List<AnswerKeywordIndex.DocumentEntry> documents = new ArrayList<>();
        chunks.put("chunk-project-a", chunk(
                "chunk-project-a", "project-a", null, "claim-project-a",
                "实现", 20));
        documents.add(document("chunk-project-a", 20, "实现"));
        chunks.put("chunk-project-b", chunk(
                "chunk-project-b", "project-b", null, "claim-project-b",
                "项目", 1));
        documents.add(document("chunk-project-b", 1, "项目"));

        if (includeCases) {
            for (int index = 0; index < 20; index++) {
                String chunkId = "chunk-case-" + index;
                chunks.put(chunkId, chunk(
                        chunkId, null, "case-" + index, "claim-case-" + index,
                        "项目", 1));
                documents.add(document(chunkId, 1, "项目"));
            }
        }

        int documentCount = documents.size();
        double averageDocumentLength = documents.stream()
                .mapToInt(AnswerKeywordIndex.DocumentEntry::getDocumentLength)
                .average()
                .orElse(0.0d);
        int projectDocumentFrequency = includeCases ? 21 : 1;
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                documentCount,
                averageDocumentLength,
                documents,
                Map.of("实现", 1, "项目", projectDocumentFrequency));
        AnswerRetrievalCorpus corpus = new AnswerRetrievalCorpus(
                keywordIndex,
                Map.of(),
                chunks,
                "test-model",
                "test-sha",
                2);
        return new RuntimeAnswerContent(
                "public-1", "hash-1", projects, cases, corpus, List.of());
    }

    private AnswerKeywordIndex.DocumentEntry document(
            String chunkId, int documentLength, String term) {
        return new AnswerKeywordIndex.DocumentEntry(
                chunkId, documentLength, Map.of(term, 1));
    }

    private AnswerRetrievalChunk chunk(
            String chunkId,
            String projectSlug,
            String caseSlug,
            String claimId,
            String term,
            int tokenCount) {
        return new AnswerRetrievalChunk(
                chunkId,
                projectSlug == null ? List.of() : List.of(projectSlug),
                caseSlug == null ? List.of() : List.of(caseSlug),
                List.of(claimId),
                List.of(term),
                term,
                tokenCount);
    }

    private AnswerKnowledge knowledge(
            AnswerSubjectType subjectType,
            String stableId,
            String slug,
            String claimId,
            String evidenceId) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId,
                AnswerClaimCategory.IMPLEMENTATION,
                "公开实现说明",
                "公开实现细节",
                AnswerAchievementStatus.IMPLEMENTED_TESTED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of("实现"),
                List.of(evidenceId));
        AnswerEvidence evidence = new AnswerEvidence(
                evidenceId,
                evidenceId,
                "公开证据",
                "DOCUMENT",
                LocalDate.of(2026, 1, 1),
                null,
                1,
                "公开摘要",
                "APPROVED",
                false);
        return new AnswerKnowledge(
                subjectType,
                stableId,
                slug,
                stableId,
                "公开摘要",
                "背景",
                List.of("职责"),
                "方案",
                List.of("决策"),
                List.of("验证"),
                "结果",
                "交接",
                "完成",
                "BACKEND",
                Set.of("JAVA"),
                List.of(),
                List.of(evidence),
                List.of(claim));
    }

    private TurnDeadline activeDeadline() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        return new TurnDeadline(Instant.parse("2026-08-18T00:01:00Z"), clock);
    }
}
