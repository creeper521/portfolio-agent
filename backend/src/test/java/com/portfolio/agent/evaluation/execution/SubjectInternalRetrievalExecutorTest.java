package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectInternalRetrievalExecutorTest {

    private final RuntimeContentSnapshot bundle = bundledSnapshot();
    private final SubjectInternalRetrievalExecutor executor =
            new SubjectInternalRetrievalExecutor(bundle);

    @Test
    void retrievesClaimsAndApprovedEvidenceOfTheReferencedSubject() {
        EvalCase evalCase = caseWithSubject("sql-audit", ClaimSubjectType.PROJECT);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput(evalCase.getId(),
                        List.of(new EvalMessage("user", "介绍 SQL 审计项目")),
                        EvalLayer.SUBJECT_INTERNAL_RETRIEVAL, 1,
                        List.of(new EvalSubjectRef(ClaimSubjectType.PROJECT, "sql-audit"))),
                new EvalRunContext("run-1", bundle.getContentVersion(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.PASS);
        assertThat(observation.getSelectedProjectSlug()).isEqualTo("sql-audit");
        assertThat(observation.getSelectedClaimIds()).isNotEmpty();
        // every returned evidence id must be an approved evidence record
        java.util.Set<String> approved = new java.util.HashSet<>();
        bundle.getApprovedEvidence().forEach(record -> approved.add(record.getId()));
        assertThat(observation.getSelectedEvidenceIds())
                .allSatisfy(id -> assertThat(approved).contains(id));
        assertThat(observation.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(observation.getReasonCodes()).isEmpty();
    }

    @Test
    void unknownSubjectIsReportedAsAnErrorNotAFakePass() {
        EvalCase evalCase = caseWithSubject("ghost-subject", ClaimSubjectType.CASE);

        EvalObservation observation = executor.execute(
                new EvalExecutionInput(evalCase.getId(),
                        List.of(new EvalMessage("user", "幽灵主体")),
                        EvalLayer.SUBJECT_INTERNAL_RETRIEVAL, 1,
                        List.of(new EvalSubjectRef(ClaimSubjectType.CASE, "ghost-subject"))),
                new EvalRunContext("run-1", bundle.getContentVersion(),
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.ERROR);
        assertThat(observation.getReasonCodes()).contains("SUBJECT_UNRESOLVABLE");
    }

    @Test
    void supportsOnlyTheSubjectInternalRetrievalLayer() {
        assertThat(executor.supports(EvalLayer.SUBJECT_INTERNAL_RETRIEVAL)).isTrue();
        assertThat(executor.supports(EvalLayer.BUNDLE_CONTRACT)).isFalse();
        assertThat(executor.supports(EvalLayer.INTELLIGENCE)).isFalse();
    }

    private EvalCase caseWithSubject(String slug, ClaimSubjectType type) {
        EvalSubjectRef subject = new EvalSubjectRef(type, slug);
        return new EvalCase(
                "legacy.test-" + slug, "测试 " + slug, com.portfolio.agent.evaluation.domain.EvalSplit.REGRESSION,
                com.portfolio.agent.evaluation.domain.EvalOrigin.HUMAN_AUTHORED,
                com.portfolio.agent.evaluation.domain.EvalRiskLevel.STANDARD,
                "APPROVED", "reviewer", "PUBLIC_BUNDLE", "legacy 回归",
                "2026-08-06.1", List.of("legacy"),
                new EvalCase.Input(List.of(new EvalMessage("user", "测试 " + slug))),
                new EvalCase.Oracle(List.of(subject)),
                new EvalCase.Expectations(
                        List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of(), List.of(), List.of(), List.of()),
                new EvalCase.Execution(List.of(EvalLayer.SUBJECT_INTERNAL_RETRIEVAL), 3),
                List.of(new com.portfolio.agent.evaluation.domain.EvalGraderRule(
                        "SUBJECT_MATCH", com.portfolio.agent.evaluation.domain.EvalSeverity.BLOCKING)),
                new EvalCase.Maintenance(List.of(subject), false));
    }

    private RuntimeContentSnapshot bundledSnapshot() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        com.portfolio.agent.portfolio.repository.file.PublicBundleLoader loader =
                new com.portfolio.agent.portfolio.repository.file.PublicBundleLoader(
                        mapper,
                        new com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator(),
                        java.time.Clock.systemUTC());
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json",
                "rag-documents.jsonl", "keyword-index.json",
                "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        return loader.load(files);
    }

    private byte[] readResource(String path) {
        try (java.io.InputStream stream = SubjectInternalRetrievalExecutorTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing classpath resource: " + path);
            }
            return stream.readAllBytes();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(
                    "unable to read classpath resource: " + path, failure);
        }
    }
}
