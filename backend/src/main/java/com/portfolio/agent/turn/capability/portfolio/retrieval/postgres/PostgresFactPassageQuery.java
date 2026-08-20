package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;

import java.util.List;

@FunctionalInterface
public interface PostgresFactPassageQuery {

    List<PostgresKnowledgePassageRow> findPassages(String releaseId, List<String> subjectIds);

    default List<PostgresKnowledgePassageRow> findRelevantPassages(
            String releaseId,
            List<String> subjectIds,
            String query,
            List<AnswerClaimCategory> preferredClaimCategories,
            int limit
    ) {
        return findPassages(releaseId, subjectIds).stream()
                .filter(passage -> PortfolioTextRelevance.matches(
                        query, passage.getContent()))
                .filter(passage -> preferredClaimCategories.isEmpty()
                        || preferredClaimCategories.contains(passage.getClaimCategory()))
                .limit(limit)
                .toList();
    }
}
