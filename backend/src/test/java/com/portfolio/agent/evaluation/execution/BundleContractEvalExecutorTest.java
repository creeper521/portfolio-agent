package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleContractEvalExecutorTest {
    @Test
    void missingPublishedClaimOrEvidenceReferenceFailsClosed() {
        RuntimeContentSnapshot snapshot = mock(RuntimeContentSnapshot.class);
        ProjectProfile project = mock(ProjectProfile.class);
        when(project.getTitle()).thenReturn("Project A");
        when(project.getSlug()).thenReturn("project-a");
        when(project.getClaimIds()).thenReturn(List.of("claim-missing"));
        when(project.getEvidenceIds()).thenReturn(List.of("evidence-missing"));
        when(snapshot.getProjects()).thenReturn(List.of(project));
        when(snapshot.getCases()).thenReturn(List.of());
        when(snapshot.getClaims()).thenReturn(List.of());
        when(snapshot.getApprovedEvidence()).thenReturn(List.of());

        BundleContractEvalExecutor executor = new BundleContractEvalExecutor(snapshot);
        EvalObservation result = executor.execute(new EvalExecutionInput(
                "case-1", List.of(new EvalMessage("user", "Project A")),
                EvalLayer.BUNDLE_CONTRACT, 1), new EvalRunContext("run", "v1"));

        assertThat(result.getStatus()).isEqualTo(EvalObservationStatus.FAIL);
        assertThat(result.getReasonCodes()).contains("REFERENCE_INCOMPLETE");
    }
}
