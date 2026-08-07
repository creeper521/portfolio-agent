package com.portfolio.agent.evaluation.application;

import com.portfolio.agent.evaluation.domain.EvalPolicy;
import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalRunConfigTest {

    @Test
    void changedSubjectsAreDeeplyDefensiveCopied() {
        Map<String, Set<String>> mutable = new LinkedHashMap<>();
        Set<String> subjects = new LinkedHashSet<>();
        subjects.add("sql-audit");
        mutable.put("2026-08-06.1", subjects);

        EvalRunConfig config = new EvalRunConfig(
                EvalRunMode.OFFLINE, TestIdentity.create(), TestPolicy.create(),
                mutable, Optional.empty(),
                EvalProviderAuthorization.MOCK_ONLY, Optional.empty());

        // mutating the caller's map or inner set must not leak into the config
        mutable.put("2026-08-06.2", Set.of("other"));
        subjects.add("leaked");
        mutable.get("2026-08-06.1").add("leaked-inner");

        assertThat(config.getChangedSubjects()).containsOnlyKeys("2026-08-06.1");
        assertThat(config.getChangedSubjects().get("2026-08-06.1"))
                .containsExactly("sql-audit");
        // and the exposed view itself is immutable
        assertThatThrownBy(() -> config.getChangedSubjects()
                .put("x", Set.of("y")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> config.getChangedSubjects()
                .get("2026-08-06.1").add("z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class TestIdentity {
        static EvalRunIdentity create() {
            return EvalRunIdentity.create(
                    "deadbeef", "2026-08-06.1", "sha256:dataset",
                    "2026-08-05.1", "sha256:bundle", "NOT_APPLICABLE",
                    "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE",
                    "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE",
                    "NOT_APPLICABLE", "NOT_APPLICABLE");
        }
    }

    private static final class TestPolicy {
        static EvalPolicy create() {
            return EvalPolicy.builder()
                    .policyId("phase-0.v1").mode("OFFLINE")
                    .blockingProvider("DEEPSEEK_V4_FLASH")
                    .publicSubjectSmokeCoverageMinimum(new java.math.BigDecimal("1.0"))
                    .defaultTrials(3).build();
        }
    }
}
