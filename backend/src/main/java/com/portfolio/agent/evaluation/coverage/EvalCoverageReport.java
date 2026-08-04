package com.portfolio.agent.evaluation.coverage;

import java.util.List;

public final class EvalCoverageReport {

    private final List<String> requiredDeepSubjects;
    private final List<String> deepCoveredSubjects;
    private final List<EvalCoverageIssue> issues;

    public EvalCoverageReport(List<String> requiredDeepSubjects, List<String> deepCoveredSubjects,
                              List<EvalCoverageIssue> issues) {
        this.requiredDeepSubjects = List.copyOf(requiredDeepSubjects);
        this.deepCoveredSubjects = List.copyOf(deepCoveredSubjects);
        this.issues = List.copyOf(issues);
    }

    public List<String> getRequiredDeepSubjects() {
        return requiredDeepSubjects;
    }

    public List<String> getDeepCoveredSubjects() {
        return deepCoveredSubjects;
    }

    public List<EvalCoverageIssue> getIssues() {
        return issues;
    }

    public boolean isValid() {
        return issues.isEmpty();
    }
}
