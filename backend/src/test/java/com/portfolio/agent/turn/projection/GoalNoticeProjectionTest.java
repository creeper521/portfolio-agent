package com.portfolio.agent.turn.projection;

import com.portfolio.agent.turn.execution.GoalCoverage;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GoalNoticeProjectionTest {
    @Test void internalTerminalBecomesBoundedUserSafeNotice() {
        SemanticTurnOutcome outcome = new SemanticTurnOutcome(
                List.of(new TaskOutcome("task-general",
                        new TaskOutcome.TimedOut())),
                List.of(new GoalCoverage("goal-general", GoalCoverage.Coverage.NONE)));
        PublicAgentTurn.Answer turn = new PublicAgentTurnProjector().project(
                UUID.randomUUID(), ProjectionTestFixtures.generalPlan(), outcome);
        assertThat(turn.getAnswer().getGoalResults().getFirst().getNotices())
                .containsExactly(new GoalNotice("TIMED_OUT", "这个目标未能在时限内完成。"));
        assertThat(turn.getAnswer().getResolution()).isEqualTo(PublicAnswer.Resolution.NO_RESULT);
    }
}
