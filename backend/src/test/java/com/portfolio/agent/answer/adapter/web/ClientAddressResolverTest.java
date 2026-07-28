package com.portfolio.agent.answer.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTest {
    @Test
    void ignoresForwardedHeaderByDefault() {
        MockHttpServletRequest request = request("198.51.100.2", "203.0.113.7");
        assertThat(new ClientAddressResolver(false, Set.of()).resolve(request))
                .isEqualTo("198.51.100.2");
    }

    @Test
    void acceptsFirstAddressOnlyFromExplicitTrustedProxy() {
        MockHttpServletRequest request = request("198.51.100.2", "203.0.113.7, 198.51.100.9");
        assertThat(new ClientAddressResolver(true, Set.of("198.51.100.2")).resolve(request))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void rejectsMalformedForwardedAddress() {
        MockHttpServletRequest request = request("198.51.100.2", "not-an-address");
        assertThat(new ClientAddressResolver(true, Set.of("198.51.100.2")).resolve(request))
                .isEqualTo("198.51.100.2");
    }

    private MockHttpServletRequest request(String remote, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
