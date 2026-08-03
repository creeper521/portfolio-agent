package com.portfolio.agent.selection.adapter.postgres;

import com.portfolio.agent.selection.domain.PostgresSelectionRow;
import com.portfolio.agent.selection.domain.SelectionTarget;
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
