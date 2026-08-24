package com.portfolio.agent.turn.capability.general;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralKnowledgeGeneratorTest {
    @Test void callsProviderExactlyOnceAndReturnsStrictResult() {
        AtomicInteger calls = new AtomicInteger();
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator(request -> {
            calls.incrementAndGet();
            return GeneralTestFixtures.VALID_EXPLANATION;
        });
        assertThat(generator.generate(GeneralTestFixtures.explanation()).getTopic()).isEqualTo("并发控制");
        assertThat(calls).hasValue(1);
    }

    @Test void invalidProviderDraftIsAClosedFailure() {
        AtomicInteger calls = new AtomicInteger();
        GeneralKnowledgeGenerator generator = GeneralTestFixtures.generator(request -> {
            calls.incrementAndGet();
            return "{}";
        });
        assertThatThrownBy(() -> generator.generate(GeneralTestFixtures.explanation()))
                .isInstanceOf(GeneralKnowledgeUnavailableException.class);
        assertThat(calls).as("schema rejection must not trigger repair").hasValue(1);
    }
}
