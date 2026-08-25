package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import java.util.List;
import java.util.Objects;

/** 知识检索结果（不可变值对象）：候选选择结果与命中的公开知识段落列表。 */
public final class PostgresKnowledgeQueryResult {

    private final CandidateRetrievalResult candidates;
    private final List<PostgresKnowledgePassageRow> passages;

    public PostgresKnowledgeQueryResult(
            CandidateRetrievalResult candidates,
            List<PostgresKnowledgePassageRow> passages) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
    }

    public CandidateRetrievalResult getCandidates() { return candidates; }
    public List<PostgresKnowledgePassageRow> getPassages() { return passages; }
}
