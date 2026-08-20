package com.portfolio.agent.turn.state.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationContextPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsEveryExplicitRuntimeModeWithoutAnImplicitFallback() {
        for (ConversationContextProperties.Mode mode : ConversationContextProperties.Mode.values()) {
            contextRunner.withPropertyValues(
                    "portfolio.conversation-context.mode=" + mode,
                    "portfolio.conversation-context.idle-ttl=30m",
                    "portfolio.conversation-context.absolute-ttl=30m")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ConversationContextProperties properties =
                                context.getBean(ConversationContextProperties.class);
                        assertThat(properties.getMode()).isEqualTo(mode);
                        assertThat(properties.getIdleTtl()).isEqualTo(Duration.ofMinutes(30));
                        assertThat(properties.getAbsoluteTtl()).isEqualTo(Duration.ofMinutes(30));
                        assertThat(properties.getClarificationTtl()).isEqualTo(Duration.ofMinutes(5));
                        assertThat(properties.getKeyRotationRetention())
                                .isEqualTo(Duration.ofMinutes(45));
                        properties.validate();
                    });
        }
    }

    @Test
    void rejectsSlidingExpiryAndIncompletePreviousKeyPairs() {
        ConversationContextProperties sliding = new ConversationContextProperties();
        sliding.setIdleTtl(Duration.ofMinutes(10));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(sliding::validate))
                .isInstanceOf(IllegalStateException.class);

        ConversationContextProperties incomplete = new ConversationContextProperties();
        incomplete.getCrypto().setPreviousPayloadKeyId("payload-v0");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(incomplete::validate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured together");

        ConversationContextProperties shortOverlap = new ConversationContextProperties();
        shortOverlap.setKeyRotationRetention(Duration.ofMinutes(44));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(shortOverlap::validate))
                .isInstanceOf(IllegalStateException.class);

        ConversationContextProperties reusedId = new ConversationContextProperties();
        reusedId.getCrypto().setCurrentTokenKeyId("token-v1");
        reusedId.getCrypto().setPreviousTokenKeyId("token-v1");
        reusedId.getCrypto().setPreviousTokenKey("previous-value");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(reusedId::validate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void bindsStableCurrentTokenAndPayloadKeysIndependently() {
        contextRunner.withPropertyValues(
                "portfolio.conversation-context.crypto.current-token-key-id=token-v1",
                "portfolio.conversation-context.crypto.current-token-key=token-value",
                "portfolio.conversation-context.crypto.current-payload-key-id=payload-v1",
                "portfolio.conversation-context.crypto.current-payload-key=payload-value")
                .run(context -> {
                    ConversationContextProperties.Crypto crypto = context
                            .getBean(ConversationContextProperties.class).getCrypto();
                    assertThat(crypto.getCurrentTokenKeyId()).isEqualTo("token-v1");
                    assertThat(crypto.getCurrentTokenKey()).isEqualTo("token-value");
                    assertThat(crypto.getCurrentPayloadKeyId()).isEqualTo("payload-v1");
                    assertThat(crypto.getCurrentPayloadKey()).isEqualTo("payload-value");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConversationContextProperties.class)
    static class PropertiesConfiguration {
    }
}
