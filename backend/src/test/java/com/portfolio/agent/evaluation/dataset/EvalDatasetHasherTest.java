package com.portfolio.agent.evaluation.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalDatasetHasherTest {

    private final EvalDatasetHasher hasher = new EvalDatasetHasher(new ObjectMapper());

    @Test
    void sameInputsYieldTheSameHash() throws Exception {
        Path first = Files.createTempFile("handwritten-a-", ".json");
        Path second = Files.createTempFile("handwritten-b-", ".json");
        Files.writeString(first, "{\"id\":\"routing.x\"}", StandardCharsets.UTF_8);
        Files.writeString(second, "{\"id\":\"answer.y\"}", StandardCharsets.UTF_8);
        List<EvalCase> generated = List.of(smoke("smoke.project.a"), smoke("smoke.case.b"));

        String once = hasher.hash(List.of(first, second), generated);
        String twice = hasher.hash(List.of(second, first), generated);

        assertThat(once).isEqualTo(twice).hasSize(64);
    }

    @Test
    void handwrittenContentChangeInvalidatesTheHash() throws Exception {
        Path file = Files.createTempFile("handwritten-", ".json");
        Files.writeString(file, "{\"id\":\"routing.x\"}", StandardCharsets.UTF_8);

        String before = hasher.hash(List.of(file), List.of());

        Files.writeString(file, "{\"id\":\"routing.y\"}", StandardCharsets.UTF_8);
        String after = hasher.hash(List.of(file), List.of());

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void generatedCaseChangeInvalidatesTheHash() {
        String before = hasher.hash(List.of(), List.of(smoke("smoke.project.a")));
        String after = hasher.hash(List.of(), List.of(smoke("smoke.project.a"),
                smoke("smoke.case.b")));

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void hashCoversOnlyRealContentAndIgnoresFileNameOrder() throws Exception {
        Path a = Files.createTempFile("a-", ".json");
        Path b = Files.createTempFile("b-", ".json");
        Files.writeString(a, "{\"id\":\"routing.x\"}", StandardCharsets.UTF_8);
        Files.writeString(b, "{\"id\":\"answer.y\"}", StandardCharsets.UTF_8);

        String forward = hasher.hash(List.of(a, b), List.of());
        String backward = hasher.hash(List.of(b, a), List.of());

        assertThat(forward).isEqualTo(backward);
    }

    private EvalCase smoke(String id) {
        return new EvalCase(
                id, "标题", EvalSplit.CALIBRATION, EvalOrigin.BUNDLE_GENERATED,
                com.portfolio.agent.evaluation.domain.EvalRiskLevel.STANDARD,
                "APPROVED", "phase-0-generator", "PUBLIC_BUNDLE", "公开主体冒烟",
                "2026-08-06.1", List.of("smoke"),
                new EvalCase.Input(List.of(new com.portfolio.agent.evaluation.domain.EvalMessage("user", "标题"))),
                new EvalCase.Oracle(List.of(new com.portfolio.agent.evaluation.domain.EvalSubjectRef(
                        com.portfolio.agent.portfolio.domain.ClaimSubjectType.PROJECT, "a"))),
                new EvalCase.Expectations(
                        List.of(com.portfolio.agent.evaluation.domain.AnswerResolution.ANSWERED),
                        List.of(com.portfolio.agent.evaluation.domain.ConversationAnswerScope.PORTFOLIO),
                        List.of(), List.of(), List.of(), List.of()),
                new EvalCase.Execution(List.of(com.portfolio.agent.evaluation.domain.EvalLayer.BUNDLE_CONTRACT), 3),
                List.of(new com.portfolio.agent.evaluation.domain.EvalGraderRule(
                        "SUBJECT_MATCH", com.portfolio.agent.evaluation.domain.EvalSeverity.BLOCKING)),
                new EvalCase.Maintenance(
                        List.of(new com.portfolio.agent.evaluation.domain.EvalSubjectRef(
                                com.portfolio.agent.portfolio.domain.ClaimSubjectType.PROJECT, "a")), true));
    }
}
