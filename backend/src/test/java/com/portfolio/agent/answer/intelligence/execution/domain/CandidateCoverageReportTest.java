package com.portfolio.agent.answer.intelligence.execution.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandidateCoverageReportTest {

    @Test
    void coveragePreservesEachRequestedTargetInsteadOfGlobalBoolean() {
        CandidateCoverageReport report = new CandidateCoverageReport(java.util.Map.of(
                "subject-a/OVERVIEW", CandidateCoverageReport.CoverageStatus.MATCHED,
                "subject-a/RESPONSIBILITY",
                CandidateCoverageReport.CoverageStatus.EVALUATED_NO_QUALIFYING_MATCH,
                "subject-b/OVERVIEW", CandidateCoverageReport.CoverageStatus.NOT_EVALUATED_BUDGET));

        assertEquals(3, report.getStatusesByTarget().size());
        assertEquals(CandidateCoverageReport.CoverageStatus.NOT_EVALUATED_BUDGET,
                report.getStatus("subject-b/OVERVIEW"));
    }
}
