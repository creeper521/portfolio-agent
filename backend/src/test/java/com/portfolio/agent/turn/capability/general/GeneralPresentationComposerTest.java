package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralPresentationComposerTest {
    private final GeneralPresentationComposer composer = new GeneralPresentationComposer();

    @Test void threeDepthFixturesKeepOneDefinitionBeforeOneMechanism() {
        for (UserGoalProposal.Depth depth : UserGoalProposal.Depth.values()) {
            GeneralSemanticResult result = new GeneralSemanticResult(
                    depth.name(), List.of(
                    new GeneralSemanticResult.Statement(
                            GeneralSemanticResult.Role.DEFINITION,
                            sentences(depth, "概念"), null, null),
                    new GeneralSemanticResult.Statement(
                            GeneralSemanticResult.Role.MECHANISM,
                            sentences(depth, "机制"), null, null)),
                    List.of("边界。"), "public-1");

            GeneralPresentation presentation = composer.compose(result);

            assertThat(presentation.getSections()).extracting(GeneralPresentation.Section::title)
                    .containsExactly("概念", "机制", "适用边界");
        }
    }

    private String sentences(UserGoalProposal.Depth depth, String prefix) {
        int count = switch (depth) {
            case CONCISE -> 1;
            case STANDARD -> 2;
            case DETAILED -> 4;
        };
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> prefix + index + "。")
                .collect(java.util.stream.Collectors.joining());
    }
}
