package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerRetrievalChunk;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.RetrievalCandidate;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.service.RankedRetrievalHit;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Creates retrieval observations without retaining query text or oracle expectations. */
public final class RetrievalObservationFactory {

    public EvalObservation fullCorpus(
            EvalExecutionInput input,
            RetrievalDecision decision,
            Map<String, AnswerRetrievalChunk> chunks,
            List<RankedRetrievalHit> keywordHits,
            List<RankedRetrievalHit> vectorHits,
            List<RetrievalCandidate> fusedCandidates,
            long durationMilliseconds
    ) {
        List<String> selectedChunkIds = decision.getSelectedChunkIds();
        return new EvalObservation(
                input.getCaseId(), EvalLayer.FULL_CORPUS_RETRIEVAL, input.getTrialIndex(),
                EvalObservationStatus.PASS, firstProjectSlug(selectedChunkIds, chunks),
                firstCaseSlug(selectedChunkIds, chunks), decision.getSelectedClaimIds(), List.of(),
                selectedChunkIds, resolution(decision.getType()), ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                reasonCodes(input.getLayer(), decision, keywordHits, vectorHits, fusedCandidates),
                durationMilliseconds, EvalProviderUsage.unavailable());
    }

    private String firstProjectSlug(List<String> selectedChunkIds,
                                    Map<String, AnswerRetrievalChunk> chunks) {
        return firstSubjectSlug(selectedChunkIds, chunks, true);
    }

    private String firstCaseSlug(List<String> selectedChunkIds,
                                 Map<String, AnswerRetrievalChunk> chunks) {
        return firstSubjectSlug(selectedChunkIds, chunks, false);
    }

    private String firstSubjectSlug(List<String> selectedChunkIds,
                                    Map<String, AnswerRetrievalChunk> chunks,
                                    boolean project) {
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

    private AnswerResolution resolution(RetrievalDecisionType type) {
        if (type == RetrievalDecisionType.SUFFICIENT) {
            return AnswerResolution.ANSWERED;
        }
        if (type == RetrievalDecisionType.AMBIGUOUS) {
            return AnswerResolution.NEEDS_CLARIFICATION;
        }
        return AnswerResolution.NOT_SUPPORTED;
    }

    private List<String> reasonCodes(
            EvalLayer requestedLayer,
            RetrievalDecision decision,
            List<RankedRetrievalHit> keywordHits,
            List<RankedRetrievalHit> vectorHits,
            List<RetrievalCandidate> fusedCandidates
    ) {
        List<String> codes = new ArrayList<String>();
        codes.add("REQUESTED_LAYER=" + requestedLayer.name());
        codes.add("RETRIEVAL_DECISION=" + decision.getType().name());
        codes.add("RETRIEVAL_MODE=" + decision.getMode().name());
        codes.add("KEYWORD_HIT_COUNT=" + keywordHits.size());
        codes.add("VECTOR_HIT_COUNT=" + vectorHits.size());
        codes.add("FUSED_HIT_COUNT=" + fusedCandidates.size());
        codes.addAll(hitRanks("KEYWORD_HIT", keywordHits));
        codes.addAll(hitRanks("VECTOR_HIT", vectorHits));
        codes.addAll(fusedRanks(fusedCandidates));
        return List.copyOf(codes);
    }

    private List<String> hitRanks(String prefix, List<RankedRetrievalHit> hits) {
        List<String> values = new ArrayList<String>();
        for (RankedRetrievalHit hit : hits) {
            values.add(prefix + "=" + hit.getChunkId() + ":" + hit.getRank());
        }
        return List.copyOf(values);
    }

    private List<String> fusedRanks(List<RetrievalCandidate> candidates) {
        List<String> values = new ArrayList<String>();
        int rank = 1;
        for (RetrievalCandidate candidate : candidates) {
            values.add("FUSED_HIT=" + candidate.getChunkId() + ":" + rank);
            rank++;
        }
        return List.copyOf(values);
    }
}
