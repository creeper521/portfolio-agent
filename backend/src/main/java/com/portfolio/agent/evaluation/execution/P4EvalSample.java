package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Content-free observation captured from the production composition seam. */
public final class P4EvalSample {
    private final Map<P4SafetyCheck, Boolean> checks;
    private final boolean mockProviderInvoked;
    private final boolean privacyCaptureSafe;

    public P4EvalSample(Map<P4SafetyCheck, Boolean> checks,
            boolean mockProviderInvoked, boolean privacyCaptureSafe) {
        Objects.requireNonNull(checks, "checks");
        EnumMap<P4SafetyCheck, Boolean> copy = new EnumMap<>(P4SafetyCheck.class);
        for (P4SafetyCheck check : P4SafetyCheck.values()) {
            if (!checks.containsKey(check)) {
                throw new IllegalArgumentException("every P4 safety check must be observed");
            }
            copy.put(check, Objects.requireNonNull(checks.get(check), "check verdict"));
        }
        this.checks = Map.copyOf(copy);
        this.mockProviderInvoked = mockProviderInvoked;
        this.privacyCaptureSafe = privacyCaptureSafe;
    }

    public Map<P4SafetyCheck, Boolean> getChecks() { return checks; }
    public boolean isMockProviderInvoked() { return mockProviderInvoked; }
    public boolean isPrivacyCaptureSafe() { return privacyCaptureSafe; }
}
