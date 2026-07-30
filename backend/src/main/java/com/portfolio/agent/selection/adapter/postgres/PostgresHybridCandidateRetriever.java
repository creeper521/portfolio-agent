package com.portfolio.agent.selection.adapter.postgres;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.PostgresSelectionRow;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.gateway.CandidateRetrievalPort;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PostgresHybridCandidateRetriever implements CandidateRetrievalPort {

    private static final double RRF_K = 60.0;

    private final PostgresSelectionQuery query;
    private final LocalEmbeddingPort embeddingPort;

    public PostgresHybridCandidateRetriever(
            PostgresSelectionQuery query,
            LocalEmbeddingPort embeddingPort) {
        this.query = Objects.requireNonNull(query, "query");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
    }

    @Override
    public CandidateRetrievalResult retrieve(SelectionTarget target, int limit) {
        ActiveRelease release;
        List<PostgresSelectionRow> ftsRows;
        try {
            release = query.activeRelease();
            ftsRows = query.searchFts(release.getReleaseId(), target, limit);
        } catch (RuntimeException exception) {
            throw new CandidateRetrievalException(
                    "public candidate retrieval is unavailable",
                    exception);
        }
        List<PostgresSelectionRow> vectorRows;
        RetrievalMode retrievalMode;
        try {
            EmbeddingVector embedding = embeddingPort.embedQuery(queryText(target));
            vectorRows = query.searchVector(
                    release.getReleaseId(),
                    embedding.copyValues(),
                    target,
                    limit);
            retrievalMode = RetrievalMode.HYBRID;
        } catch (RuntimeException exception) {
            if (target.getCapabilityCodes().isEmpty() && target.getCareerTrack() == null) {
                throw new CandidateRetrievalException(
                        "public vector retrieval is unavailable",
                        exception);
            }
            vectorRows = List.of();
            retrievalMode = RetrievalMode.FTS_ONLY;
        }

        List<SelectionCandidate> candidates = fuse(ftsRows, vectorRows, limit);
        return new CandidateRetrievalResult(
                release.getReleaseVersion(),
                retrievalMode,
                candidates);
    }

    private List<SelectionCandidate> fuse(
            List<PostgresSelectionRow> ftsRows,
            List<PostgresSelectionRow> vectorRows,
            int limit) {
        Map<String, FusedCandidate> fused = new LinkedHashMap<>();
        addRanked(fused, ftsRows);
        addRanked(fused, vectorRows);
        return fused.values().stream()
                .sorted(Comparator
                        .comparingDouble(FusedCandidate::rrfScore)
                        .reversed()
                        .thenComparing(value -> value.row().getSubjectId()))
                .limit(limit)
                .map(this::toCandidate)
                .toList();
    }

    private void addRanked(
            Map<String, FusedCandidate> fused,
            List<PostgresSelectionRow> rows) {
        for (int index = 0; index < rows.size(); index++) {
            PostgresSelectionRow row = rows.get(index);
            double contribution = 1.0 / (RRF_K + index + 1);
            FusedCandidate current = fused.get(row.getSubjectId());
            if (current == null) {
                fused.put(row.getSubjectId(), new FusedCandidate(row, contribution));
            } else {
                fused.put(row.getSubjectId(), new FusedCandidate(
                        mergeRows(current.row(), row),
                        current.rrfScore() + contribution));
            }
        }
    }

    private PostgresSelectionRow mergeRows(
            PostgresSelectionRow left,
            PostgresSelectionRow right) {
        List<String> allCapabilities = new ArrayList<>(left.getCapabilityCodes());
        allCapabilities.addAll(right.getCapabilityCodes());
        Map<String, EvidenceReference> evidence = new LinkedHashMap<>();
        left.getEvidenceReferences().forEach(reference ->
                evidence.put(reference.getClaimId() + "\n" + reference.getEvidenceId(), reference));
        right.getEvidenceReferences().forEach(reference ->
                evidence.put(reference.getClaimId() + "\n" + reference.getEvidenceId(), reference));
        return new PostgresSelectionRow(
                left.getSubjectId(),
                left.getSubjectKind(),
                left.getTitle(),
                left.getSummary(),
                left.getRoute(),
                left.getCareerTrack(),
                Set.copyOf(allCapabilities),
                List.copyOf(evidence.values()),
                Math.max(left.getEvidenceQuality(), right.getEvidenceQuality()));
    }

    private SelectionCandidate toCandidate(FusedCandidate fused) {
        PostgresSelectionRow row = fused.row();
        return new SelectionCandidate(
                row.getSubjectId(),
                row.getSubjectKind(),
                row.getTitle(),
                row.getSummary(),
                row.getRoute(),
                row.getCareerTrack(),
                row.getCapabilityCodes(),
                row.getEvidenceReferences(),
                Math.min(1.0, fused.rrfScore() * 30.0),
                row.getEvidenceQuality(),
                0.0);
    }

    private String queryText(SelectionTarget target) {
        List<String> parts = new ArrayList<>();
        if (target.getCareerTrack() != null) {
            parts.add(target.getCareerTrack());
        }
        parts.add(target.getAudienceRole());
        parts.addAll(target.getCapabilityCodes().stream().sorted().toList());
        if (target.getGoal() != null) {
            parts.add(target.getGoal());
        }
        return String.join(" ", parts);
    }

    private static final class FusedCandidate {

        private final PostgresSelectionRow row;
        private final double rrfScore;

        private FusedCandidate(PostgresSelectionRow row, double rrfScore) {
            this.row = row;
            this.rrfScore = rrfScore;
        }

        private PostgresSelectionRow row() {
            return row;
        }

        private double rrfScore() {
            return rrfScore;
        }
    }
}
