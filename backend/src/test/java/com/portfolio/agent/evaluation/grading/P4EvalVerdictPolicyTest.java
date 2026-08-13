package com.portfolio.agent.evaluation.grading;

import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import com.portfolio.agent.evaluation.execution.P4EvalExecutor;
import com.portfolio.agent.evaluation.execution.P4EvalReport;
import com.portfolio.agent.evaluation.execution.P4EvalSample;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class P4EvalVerdictPolicyTest {
    @Test
    void mockVerifiedBuildIsOrdinaryCiPassButReleaseIncomplete() {
        P4EvalReport report = new P4EvalExecutor().execute(
                List.of(sample()), false, false, false, false, false);
        P4EvalVerdictPolicy policy = new P4EvalVerdictPolicy();

        assertThat(policy.ordinaryCiPasses(report)).isTrue();
        assertThat(policy.releaseVerdict(report)).isEqualTo(EvalVerdict.INCOMPLETE);
    }

    @Test
    void allExecutedAndPassingDimensionsCanRelease() {
        P4EvalReport report = new P4EvalExecutor().execute(
                List.of(sample()), true, true, true, true, true);

        assertThat(new P4EvalVerdictPolicy().releaseVerdict(report))
                .isEqualTo(EvalVerdict.PASS);
    }

    private P4EvalSample sample() {
        Map<P4SafetyCheck, Boolean> checks = new EnumMap<>(P4SafetyCheck.class);
        for (P4SafetyCheck check : P4SafetyCheck.values()) checks.put(check, true);
        return new P4EvalSample(checks, true, true);
    }
}
