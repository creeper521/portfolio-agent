package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSuite;
import com.portfolio.agent.release.benchmark.RetrievalComparisonRunner;
import com.portfolio.agent.release.benchmark.RetrievalRouteEvaluation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exposes the existing expected-subject benchmark as a clearly scoped diagnostic.
 * This class intentionally does not implement {@link EvalExecutor}.
 */
public final class LegacyRetrievalBenchmarkAdapter {

    private final RetrievalComparisonRunner runner;

    public LegacyRetrievalBenchmarkAdapter(RetrievalComparisonRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    public List<EvalObservation> run(
            RetrievalBenchmarkSuite suite,
            RuntimeContentSnapshot snapshot,
            RetrievalPolicy policy
    ) {
        List<RetrievalRouteEvaluation> evaluations = runner.run(suite, snapshot, policy);
        Map<String, AnswerRetrievalChunk> chunks = chunks(snapshot);
        List<EvalObservation> observations = new ArrayList<EvalObservation>();
        for (RetrievalRouteEvaluation evaluation : evaluations) {
            observations.add(observation(evaluation, chunks));
        }
        return List.copyOf(observations);
    }

    private EvalObservation observation(
            RetrievalRouteEvaluation evaluation,
            Map<String, AnswerRetrievalChunk> chunks
    ) {
        return new EvalObservation(
                evaluation.getCaseId(), EvalLayer.SUBJECT_INTERNAL_RETRIEVAL, 1,
                EvalObservationStatus.PASS,
                firstSubjectSlug(evaluation.getSelectedChunkIds(), chunks, true),
                firstSubjectSlug(evaluation.getSelectedChunkIds(), chunks, false),
                evaluation.getSelectedClaimIds(), List.of(), evaluation.getSelectedChunkIds(),
                resolution(evaluation.getActualDecision()), ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                reasonCodes(evaluation), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }

    private AnswerResolution resolution(RetrievalDecisionType decision) {
        if (decision == RetrievalDecisionType.SUFFICIENT) {
            return AnswerResolution.ANSWERED;
        }
        if (decision == RetrievalDecisionType.AMBIGUOUS) {
            return AnswerResolution.NEEDS_CLARIFICATION;
        }
        return AnswerResolution.NOT_SUPPORTED;
    }

    private List<String> reasonCodes(RetrievalRouteEvaluation evaluation) {
        List<String> values = new ArrayList<String>();
        values.add("DIAGNOSTIC_SCOPE=SUBJECT_INTERNAL_RETRIEVAL");
        values.add("LEGACY_ROUTE=" + evaluation.getRoute().name());
        values.add("ACTUAL_DECISION=" + evaluation.getActualDecision().name());
        return List.copyOf(values);
    }

    private Map<String, AnswerRetrievalChunk> chunks(RuntimeContentSnapshot snapshot) {
        Map<String, AnswerRetrievalChunk> result = new LinkedHashMap<String, AnswerRetrievalChunk>();
        for (RagDocument document : snapshot.getRetrievalContent().orElseThrow(
                () -> new IllegalArgumentException(
                        "legacy retrieval benchmark requires published retrieval content"))
                .getDocuments()) {
            result.put(document.getChunkId(), new AnswerRetrievalChunk(
                    document.getChunkId(), document.getProjectSlugs(), document.getCaseSlugs(),
                    document.getClaimIds(), document.getTopics(), document.getText().length()));
        }
        return Map.copyOf(result);
    }

    private String firstSubjectSlug(
            List<String> selectedChunkIds,
            Map<String, AnswerRetrievalChunk> chunks,
            boolean project
    ) {
        for (String chunkId : selectedChunkIds) {
            AnswerRetrievalChunk chunk = chunks.get(chunkId);
            if (chunk == null) {
                continue;
            }
            List<String> slugs = project ? chunk.getProjectSlugs() : chunk.getCaseSlugs();
            if (!slugs.isEmpty()) {
                return slugs.getFirst();
            }
        }
        return null;
    }
}
