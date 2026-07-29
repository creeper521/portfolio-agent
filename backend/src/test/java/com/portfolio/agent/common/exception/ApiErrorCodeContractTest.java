package com.portfolio.agent.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorCodeContractTest {

    @Test
    void publishedPublicResourceCodesRemainStable() {
        assertThat(PublicResourceErrorCode.PROJECT_NOT_FOUND.getCode())
                .isEqualTo("PROJECT_NOT_FOUND");
        assertThat(PublicResourceErrorCode.CASE_NOT_FOUND.getCode())
                .isEqualTo("CASE_NOT_FOUND");
    }
}
