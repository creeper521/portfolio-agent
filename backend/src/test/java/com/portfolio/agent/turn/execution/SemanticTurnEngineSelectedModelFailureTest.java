package com.portfolio.agent.turn.execution;

import com.portfolio.agent.infrastructure.model.SelectedModelFailureException;
import com.portfolio.agent.infrastructure.model.StructuredModelFailure;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SemanticTurnEngineSelectedModelFailureTest {
    @Test
    void selectedModelFailureEscapesTheParallelTaskBoundaryForTurnSettlement() {
        SelectedModelFailureException selectedFailure =
                SelectedModelFailureException.from(new StructuredModelFailure(
                        StructuredModelFailure.Code.PROVIDER_UNAVAILABLE));
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override public SemanticTask.SourceDomain getSourceDomain() {
                return SemanticTask.SourceDomain.GENERAL;
            }

            @Override public TaskExecutionResult execute(TaskExecutionContext context) {
                throw selectedFailure;
            }
        };

        try (java.util.concurrent.ExecutorService pool =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            SemanticTurnEngine engine = new SemanticTurnEngine(
                    List.of(executor), pool, 1);

            SelectedModelFailureException propagated = catchThrowableOfType(
                    () -> engine.execute(
                            ExecutionTestPlanFactory.oneGeneralTask(),
                            TurnDeadline.after(
                                    Duration.ofSeconds(2), Clock.systemUTC()),
                            new CancellationSignal(), false),
                    SelectedModelFailureException.class);

            assertThat(propagated).isSameAs(selectedFailure);
        }
    }
}
