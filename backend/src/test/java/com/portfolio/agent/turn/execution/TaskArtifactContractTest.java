package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskArtifactContractTest {
    @Test
    void keepsSemanticResultPresentationAndPublicProvenanceAsDistinctAuthorities() {
        Result result = new Result();
        Presentation presentation = new Presentation();
        TaskArtifact artifact = new TaskArtifact(
                result, presentation, new TaskProvenance(List.of("source-1")));

        assertThat(artifact.getSemanticResult()).isSameAs(result);
        assertThat(artifact.getPresentation()).isSameAs(presentation);
        assertThat(artifact.getProvenance().getPublicSourceKeys()).containsExactly("source-1");
    }

    private static final class Result implements TaskSemanticResult { }
    private static final class Presentation implements TaskPresentation { }
}
