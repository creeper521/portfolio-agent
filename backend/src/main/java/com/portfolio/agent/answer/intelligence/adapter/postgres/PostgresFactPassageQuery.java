package com.portfolio.agent.answer.intelligence.adapter.postgres;

import java.util.List;

@FunctionalInterface
public interface PostgresFactPassageQuery {

    List<PostgresKnowledgePassageRow> findPassages(String releaseId, List<String> subjectIds);
}
