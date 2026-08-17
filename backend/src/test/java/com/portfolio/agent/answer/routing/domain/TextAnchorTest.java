package com.portfolio.agent.answer.routing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextAnchorTest {

    @Test
    void resolvesTheRequestedNonOverlappingOccurrenceWithJavaUtf16Offsets() {
        TextAnchor.TextSpan span = new TextAnchor("😀", 2).resolveIn("😀 x 😀");

        assertThat(span.getStartInclusive()).isEqualTo(5);
        assertThat(span.getEndExclusive()).isEqualTo(7);
        assertThat(span.getText()).isEqualTo("😀");
    }

    @Test
    void rejectsBlankAnchorsAndMissingOccurrences() {
        assertThatThrownBy(() -> new TextAnchor(" ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextAnchor("item", 2).resolveIn("item"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
