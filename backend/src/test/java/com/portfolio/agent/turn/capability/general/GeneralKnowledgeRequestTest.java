package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.turn.execution.TurnDeadline;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralKnowledgeRequestTest {
    @Test void explanationCarriesTypedFieldsAndAbsoluteDeadline() {
        GeneralKnowledgeRequest request = GeneralTestFixtures.explanation();
        assertThat(request.getKind()).isEqualTo(GeneralKnowledgeRequest.Kind.EXPLANATION);
        assertThat(request.getTopic()).isEqualTo("并发控制");
        assertThat(request.getSubjects()).isEmpty();
        assertThat(request.getDeadline().getExpiresAt()).isNotNull();
    }

    @Test void comparisonRejectsMissingDimensions() {
        TurnDeadline deadline = TurnDeadline.after(Duration.ofSeconds(1), Clock.systemUTC());
        assertThatThrownBy(() -> GeneralKnowledgeRequest.comparison(
                List.of("A", "B"), Set.of(), GeneralKnowledgeRequest.Audience.GUEST,
                "public-1", deadline)).isInstanceOf(IllegalArgumentException.class);
    }
}
