package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;

import java.util.Objects;

public final class ProviderFailureCodeMapper {

    private ProviderFailureCodeMapper() {
    }

    public static ProviderFailureCode map(
            ConversationModelFailureCode failureCode
    ) {
        return switch (Objects.requireNonNull(failureCode, "failureCode")) {
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

    public static ProviderFailureCode map(
            ModelExpressionFailureCode failureCode
    ) {
        return switch (Objects.requireNonNull(failureCode, "failureCode")) {
            case TIMEOUT -> ProviderFailureCode.PROVIDER_TIMEOUT;
            case PROVIDER_ERROR ->
                    ProviderFailureCode.PROVIDER_CONNECTION_FAILED;
            case EMPTY_RESPONSE ->
                    ProviderFailureCode.PROVIDER_EMPTY_RESPONSE;
            case INVALID_RESPONSE ->
                    ProviderFailureCode.PROVIDER_INVALID_RESPONSE;
            case REQUEST_BUILD_FAILED ->
                    ProviderFailureCode.PROVIDER_REQUEST_BUILD_FAILED;
            case DRAFT_REJECTED ->
                    ProviderFailureCode.PROVIDER_DRAFT_REJECTED;
        };
    }
}
