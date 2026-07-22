package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerRetrievalCorpus;
import com.portfolio.agent.answer.domain.AnswerTimelineEvent;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeVectorIndex;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPortfolioKnowledgeAdapterTest {

    @Test
    void projectsTypedRetrievalContentWithoutChunkText() {
        PortfolioSnapshot snapshot = snapshot(
                List.of(project("project-1", "sql-audit", List.of())),
                List.of(), List.of());
        RagDocument document = new RagDocument(
                "chunk-1", snapshot.getContentVersion(), List.of("sql-audit"),
                List.of("claim-1"), "Private-to-retrieval chunk text",
                List.of("DELIVERY"), LocalDate.parse("2026-07-01"), null, "sha256:test");
        RuntimeKeywordIndex keywordIndex = new RuntimeKeywordIndex(
                1, 1.0,
                List.of(new RuntimeKeywordIndex.DocumentEntry(
                        "chunk-1", 1, Map.of("交付", 1))),
                Map.of("交付", 1));
        RuntimeRetrievalContent retrieval = new RuntimeRetrievalContent(
                new RetrievalManifest(
                        "hybrid-rag-v1", "nfkc-bigram-v1", "retrieval-policy-v1",
                        "BAAI/bge-small-zh-v1.5", "sha256:model", 512, 256,
                        "L2", "COSINE", 1, "sha256:chunks",
                        "keyword-index-v1", "vector-index-v1"),
                List.of(document), keywordIndex,
                new RuntimeVectorIndex(2, Map.of("chunk-1", new float[]{1.0f, 0.0f})));
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot, retrieval));

        AnswerRetrievalCorpus corpus = adapter.getContent().getRetrievalCorpus().orElseThrow();

        assertThat(corpus.getChunks().get("chunk-1").getTextLength())
                .isEqualTo(document.getText().length());
        assertThat(corpus.getChunks().get("chunk-1").getClaimIds())
                .containsExactly("claim-1");
        assertThat(corpus.getKeywordIndex().getDocumentCount()).isEqualTo(1);
        assertThat(corpus.copyVectors()).containsKey("chunk-1");
        assertThat(corpus.getChunks().get("chunk-1").getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("text");
    }

    @Test
    void mapsOnlyKnowledgeOwnedByRequestedProject() {
        ProjectProfile requested = project(
                "project-1",
                "sql-audit",
                List.of("evidence-1")
        );
        ProjectProfile other = project(
                "project-2",
                "other-project",
                List.of("evidence-2")
        );
        PortfolioSnapshot snapshot = snapshot(
                List.of(other, requested),
                List.of(
                        question("question-2", "project-2"),
                        question("listed-for-wrong-project", "project-2"),
                        question("question-1", "project-1")
                ),
                List.of(
                        evidence("evidence-2", EvidenceStatus.APPROVED, false),
                        evidence("evidence-1", EvidenceStatus.APPROVED, false)
                )
        );
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot));

        Optional<AnswerKnowledge> result = adapter.findBySlug("sql-audit");

        assertThat(result).isPresent();
        AnswerKnowledge knowledge = result.orElseThrow();
        assertThat(knowledge.getSlug()).isEqualTo("sql-audit");
        assertThat(knowledge.getTitle()).isEqualTo("SQL Audit");
        assertThat(knowledge.getBackground()).isEqualTo("Background");
        assertThat(knowledge.getResponsibilities()).containsExactly("Responsibility");
        assertThat(knowledge.getSolution()).isEqualTo("Solution");
        assertThat(knowledge.getKeyDecisions()).containsExactly("Decision");
        assertThat(knowledge.getVerification()).containsExactly("Verified");
        assertThat(knowledge.getOutcome()).isEqualTo("Outcome");
        assertThat(knowledge.getHandoff()).isEqualTo("Handoff");
        assertThat(knowledge.getStatus()).isEqualTo("DELIVERED");
        assertThat(knowledge.getQuestions())
                .extracting(AnswerQuestion::getCanonicalQuestion)
                .containsExactly("Canonical question-1");
        assertThat(knowledge.getQuestions().get(0).getAliases())
                .containsExactly("Alias question-1");
        assertThat(knowledge.getQuestions().get(0).getSuggestion())
                .isEqualTo("Canonical question-1");
        assertThat(knowledge.getEvidence())
                .extracting(AnswerEvidence::getId)
                .containsExactly("evidence-1");
        assertThat(knowledge.getEvidence().get(0).getType()).isEqualTo("DOCUMENT");
        assertThat(knowledge.getEvidence().get(0).getPublicStatus()).isEqualTo("APPROVED");
        assertThat(knowledge.getEvidence().get(0).isRawContentPublic()).isFalse();
    }

    @Test
    void mapsReviewedUtf8QuestionAndEvidenceContractFromPublicSnapshot() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("public-data/public-portfolio.v1.json");
        PortfolioSnapshot snapshot;
        try (InputStream inputStream = resource.getInputStream()) {
            snapshot = new ObjectMapper()
                    .findAndRegisterModules()
                    .readValue(inputStream, PortfolioSnapshot.class);
        }
        QuestionDefinition publishedQuestion = snapshot.getQuestions().getFirst();
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot));

        AnswerKnowledge knowledge = adapter.findBySlug("sql-audit").orElseThrow();

        assertThat(knowledge.getQuestions()).hasSize(1);
        AnswerQuestion mappedQuestion = knowledge.getQuestions().getFirst();
        assertThat(mappedQuestion.getCanonicalQuestion())
                .isEqualTo(publishedQuestion.getText());
        assertThat(mappedQuestion.getAliases()).containsExactlyElementsOf(
                publishedQuestion.getAliases()
        );
        assertThat(mappedQuestion.getSuggestion()).isEqualTo(publishedQuestion.getText());
        assertThat(knowledge.getEvidence())
                .extracting(AnswerEvidence::getId)
                .containsExactly("sql-audit-delivery-set");
        assertThat(knowledge.getClaims()).hasSize(1);
        assertThat(knowledge.getClaims().getFirst().getId())
                .isEqualTo("claim-sql-audit-delivered");
        assertThat(knowledge.getClaims().getFirst().getDirectEvidenceIds())
                .containsExactly("sql-audit-delivery-set");
    }

    @Test
    void projectsReviewedTimelineIntoTheSameRuntimeAnswerContent() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("public-data/public-portfolio.v1.json");
        PortfolioSnapshot snapshot;
        try (InputStream inputStream = resource.getInputStream()) {
            snapshot = new ObjectMapper()
                    .findAndRegisterModules()
                    .readValue(inputStream, PortfolioSnapshot.class);
        }
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot));

        List<AnswerTimelineEvent> timeline = adapter.getContent().getTimeline();

        assertThat(timeline).hasSize(1);
        assertThat(timeline.getFirst().getProjectSlugs()).containsExactly("sql-audit");
        assertThat(timeline.getFirst().getClaimIds())
                .containsExactly("claim-sql-audit-delivered");
        assertThat(timeline.getFirst().getEvidenceIds())
                .containsExactly("sql-audit-delivery-set");
        assertThat(adapter.getContent().getCapabilities().isPresetAnswers()).isTrue();
        assertThat(adapter.getContent().getCapabilities().isReadOnlyTools()).isTrue();
        assertThat(adapter.getContent().getCapabilities().isMultiTurnReferences()).isTrue();
    }

    @Test
    void preservesQuestionAndEvidenceOrder() {
        ProjectProfile requested = project(
                "project-1",
                "sql-audit",
                List.of("evidence-1", "evidence-2")
        );
        PortfolioSnapshot snapshot = snapshot(
                List.of(requested),
                List.of(
                        question("question-2", "project-1"),
                        question("unrelated-question", "another-project"),
                        question("question-1", "project-1")
                ),
                List.of(
                        evidence("evidence-2", EvidenceStatus.APPROVED, false),
                        evidence("unrelated-evidence", EvidenceStatus.APPROVED, false),
                        evidence("evidence-1", EvidenceStatus.APPROVED, false)
                )
        );
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot));

        AnswerKnowledge knowledge = adapter.findBySlug("sql-audit").orElseThrow();

        assertThat(knowledge.getQuestions())
                .extracting(AnswerQuestion::getCanonicalQuestion)
                .containsExactly("Canonical question-2", "Canonical question-1");
        assertThat(knowledge.getEvidence())
                .extracting(AnswerEvidence::getId)
                .containsExactly("evidence-2", "evidence-1");
    }

    @Test
    void filtersEvidenceThatIsNotApprovedOrExposesRawContent() {
        ProjectProfile requested = project(
                "project-1",
                "sql-audit",
                List.of(
                        "approved-safe",
                        "approved-raw",
                        "pending-safe",
                        "rejected-safe",
                        "approved-unknown-raw-status"
                )
        );
        PortfolioSnapshot snapshot = snapshot(
                List.of(requested),
                List.of(),
                List.of(
                        evidence("approved-safe", EvidenceStatus.APPROVED, false),
                        evidence("approved-raw", EvidenceStatus.APPROVED, true),
                        evidence("pending-safe", EvidenceStatus.PENDING, false),
                        evidence("rejected-safe", EvidenceStatus.REJECTED, false),
                        evidence("approved-unknown-raw-status", EvidenceStatus.APPROVED, null)
                )
        );
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot));

        AnswerKnowledge knowledge = adapter.findBySlug("sql-audit").orElseThrow();

        assertThat(knowledge.getEvidence())
                .extracting(AnswerEvidence::getId)
                .containsExactly("approved-safe");
    }

    @Test
    void returnsEmptyForUnknownProject() {
        PortfolioSnapshot snapshot = snapshot(
                List.of(project("project-1", "sql-audit", List.of())),
                List.of(),
                List.of()
        );
        LocalPortfolioKnowledgeAdapter adapter =
                new LocalPortfolioKnowledgeAdapter(repository(snapshot));

        Optional<AnswerKnowledge> result = adapter.findBySlug("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void answerModelsDefensivelyCopyCollectionsAndKeepCompleteValueSemantics() {
        List<String> aliases = new ArrayList<>(List.of("Alias"));
        AnswerQuestion question = new AnswerQuestion("Canonical", aliases, "Suggestion");
        AnswerQuestion equalQuestion =
                new AnswerQuestion("Canonical", List.of("Alias"), "Suggestion");

        AnswerEvidence evidence = new AnswerEvidence(
                "evidence-1",
                "Evidence",
                "DOCUMENT",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                2,
                "Summary",
                "APPROVED",
                false
        );
        AnswerEvidence equalEvidence = new AnswerEvidence(
                "evidence-1",
                "Evidence",
                "DOCUMENT",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                2,
                "Summary",
                "APPROVED",
                false
        );

        List<String> responsibilities = new ArrayList<>(List.of("Responsibility"));
        List<String> keyDecisions = new ArrayList<>(List.of("Decision"));
        List<String> verification = new ArrayList<>(List.of("Verified"));
        List<AnswerQuestion> questions = new ArrayList<>(List.of(question));
        List<AnswerEvidence> evidenceList = new ArrayList<>(List.of(evidence));
        AnswerKnowledge knowledge = new AnswerKnowledge(
                "sql-audit",
                "SQL Audit",
                "Background",
                responsibilities,
                "Solution",
                keyDecisions,
                verification,
                "Outcome",
                "Handoff",
                "DELIVERED",
                questions,
                evidenceList
        );
        AnswerKnowledge equalKnowledge = new AnswerKnowledge(
                "sql-audit",
                "SQL Audit",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verified"),
                "Outcome",
                "Handoff",
                "DELIVERED",
                List.of(equalQuestion),
                List.of(equalEvidence)
        );

        aliases.add("Later alias");
        responsibilities.add("Later responsibility");
        keyDecisions.add("Later decision");
        verification.add("Later verification");
        questions.clear();
        evidenceList.clear();

        assertThat(question.getAliases()).containsExactly("Alias");
        assertThat(knowledge.getResponsibilities()).containsExactly("Responsibility");
        assertThat(knowledge.getKeyDecisions()).containsExactly("Decision");
        assertThat(knowledge.getVerification()).containsExactly("Verified");
        assertThat(knowledge.getQuestions()).containsExactly(question);
        assertThat(knowledge.getEvidence()).containsExactly(evidence);
        assertThatThrownBy(() -> question.getAliases().add("Forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> knowledge.getResponsibilities().add("Forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> knowledge.getKeyDecisions().add("Forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> knowledge.getVerification().add("Forbidden"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> knowledge.getQuestions().add(equalQuestion))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> knowledge.getEvidence().add(equalEvidence))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(question).isEqualTo(equalQuestion);
        assertThat(question.hashCode()).isEqualTo(equalQuestion.hashCode());
        assertThat(question.toString()).contains("Canonical", "Alias", "Suggestion");
        assertThat(question).isNotEqualTo(
                new AnswerQuestion("Canonical", List.of("Alias"), "Different suggestion")
        );
        assertThat(evidence).isEqualTo(equalEvidence);
        assertThat(evidence.hashCode()).isEqualTo(equalEvidence.hashCode());
        assertThat(evidence.toString()).contains("evidence-1", "DOCUMENT", "APPROVED");
        assertThat(evidence).isNotEqualTo(new AnswerEvidence(
                "evidence-1",
                "Evidence",
                "DOCUMENT",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                2,
                "Summary",
                "APPROVED",
                true
        ));
        assertThat(knowledge).isEqualTo(equalKnowledge);
        assertThat(knowledge.hashCode()).isEqualTo(equalKnowledge.hashCode());
        assertThat(knowledge.toString()).contains("sql-audit", "DELIVERED");
        assertThat(knowledge).isNotEqualTo(new AnswerKnowledge(
                "sql-audit",
                "SQL Audit",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Verified"),
                "Outcome",
                "Handoff",
                "IN_PROGRESS",
                List.of(equalQuestion),
                List.of(equalEvidence)
        ));
    }

    private static PublicPortfolioRepository repository(PortfolioSnapshot snapshot) {
        return repository(snapshot, null);
    }

    private static PublicPortfolioRepository repository(
            PortfolioSnapshot snapshot,
            RuntimeRetrievalContent retrieval
    ) {
        return new PublicPortfolioRepository() {
            @Override
            public RuntimeContentSnapshot getSnapshot() {
                return new RuntimeContentSnapshot(
                        snapshot,
                        "sha256:test-runtime-bundle",
                        Instant.parse("2026-07-21T00:00:00Z"),
                        retrieval
                );
            }
        };
    }

    private static PortfolioSnapshot snapshot(
            List<ProjectProfile> projects,
            List<QuestionDefinition> questions,
            List<EvidenceRecord> evidence
    ) {
        return new PortfolioSnapshot(
                "1.0",
                "2026-07-14.1",
                OffsetDateTime.parse("2026-07-14T12:00:00+08:00"),
                null,
                projects,
                List.of(),
                List.of(),
                questions,
                evidence,
                List.of()
        );
    }

    private static ProjectProfile project(
            String id,
            String slug,
            List<String> evidenceIds
    ) {
        return new ProjectProfile(
                id,
                "P-01",
                slug,
                "SQL Audit",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Java"),
                List.of("Verified"),
                "Outcome",
                "Handoff",
                ProjectStatus.DELIVERED,
                ContributionType.PRIMARY,
                List.of(),
                evidenceIds,
                List.of()
        );
    }

    private static QuestionDefinition question(String id, String projectId) {
        return new QuestionDefinition(
                id,
                "Canonical " + id,
                List.of("Alias " + id),
                List.of("INTERVIEWER"),
                List.of(projectId),
                List.of("OVERVIEW"),
                List.of(com.portfolio.agent.portfolio.domain.ClaimCategory.OUTCOME),
                List.of("HOME"),
                true,
                10
        );
    }

    private static EvidenceRecord evidence(
            String id,
            EvidenceStatus status,
            Boolean rawContentPublic
    ) {
        return new EvidenceRecord(
                id,
                "E-01",
                "Evidence " + id,
                EvidenceType.DOCUMENT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                2,
                "Summary " + id,
                status,
                rawContentPublic
        );
    }
}
