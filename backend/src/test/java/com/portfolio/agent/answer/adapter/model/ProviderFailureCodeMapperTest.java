package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.service.ProviderFailureCode;
import com.portfolio.agent.answer.service.ProviderFailureCodeMapper;
import com.portfolio.agent.common.observability.DiagnosticCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFailureCodeMapperTest {

    @Test
    void mapsEveryConversationFailureToOneClosedProviderFailureCode() {
        assertThat(ConversationModelFailureCode.values()).allSatisfy(failureCode ->
                assertThat(ProviderFailureCodeMapper.map(failureCode))
                        .isEqualTo(expected(failureCode)));
    }

    @Test
    void everyProviderFailureIsAStableDiagnosticCode() {
        assertThat(ProviderFailureCode.values()).allSatisfy(failureCode -> {
            assertThat(failureCode).isInstanceOf(DiagnosticCode.class);
            assertThat(failureCode.code()).isEqualTo(failureCode.name());
        });
        assertThat(java.util.Arrays.stream(ProviderFailureCode.values())
                .map(ProviderFailureCode::code))
                .containsExactly(
                        "PROVIDER_TIMEOUT",
                        "PROVIDER_CONNECTION_FAILED",
                        "PROVIDER_EMPTY_RESPONSE",
                        "PROVIDER_INVALID_RESPONSE",
                        "PROVIDER_REQUEST_BUILD_FAILED",
                        "PROVIDER_DRAFT_REJECTED",
                        "PROVIDER_DISABLED");
    }

    private ProviderFailureCode expected(
            ConversationModelFailureCode failureCode
    ) {
        return switch (failureCode) {
            case TIMEOUT -> ProviderFailureCode.PROVIDER_TIMEOUT;
            case PROVIDER_ERROR ->
                    ProviderFailureCode.PROVIDER_CONNECTION_FAILED;
            case EMPTY_RESPONSE ->
                    ProviderFailureCode.PROVIDER_EMPTY_RESPONSE;
            case INVALID_RESPONSE ->
                    ProviderFailureCode.PROVIDER_INVALID_RESPONSE;
            case REQUEST_BUILD_FAILED ->
                    ProviderFailureCode.PROVIDER_REQUEST_BUILD_FAILED;
            case DISABLED -> ProviderFailureCode.PROVIDER_DISABLED;
        };
    }
}
