package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.util.List;

public interface PostgresSelectionQuery {

    ActiveRelease activeRelease();

    List<PostgresSelectionRow> searchFts(
            String releaseId,
            SelectionTarget target,
            int limit);

    List<PostgresSelectionRow> searchVector(
            String releaseId,
            float[] embedding,
            SelectionTarget target,
            int limit);

    default List<PostgresSelectionRow> findByIds(
            String releaseId,
            List<String> subjectIds,
            SelectionTarget target) {
        throw new UnsupportedOperationException("exact subject lookup is not implemented");
    }
}
