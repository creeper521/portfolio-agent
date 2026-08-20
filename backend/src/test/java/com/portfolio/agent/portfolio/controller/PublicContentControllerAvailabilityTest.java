package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.portfolio.dto.response.PublicContentResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.infrastructure.model.policy.ConversationProviderAccess;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicContentControllerAvailabilityTest {
    @Test
    void disabledStateProjectsUnavailableWithoutDependingOnTurnTypes() {
        PortfolioService service = mock(PortfolioService.class);
        PortfolioResponseMapper mapper = mock(PortfolioResponseMapper.class);
        PublicContent content = mock(PublicContent.class);
        PublicContentResponse response = mock(PublicContentResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PublicContentController(
                service, mapper, "DISABLED", "ENABLED",
                new ConversationProviderAccess(true))
                .getPublicContent();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content), availability.capture());
        assertThat(availability.getValue().getStatus())
                .isEqualTo(AgentAvailabilityResponse.Status.UNAVAILABLE);
        assertThat(availability.getValue().getFreeTextSemanticRouting())
                .isEqualTo(
                        AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
    }

    @Test
    void persistentStateProjectsAvailable() {
        PortfolioService service = mock(PortfolioService.class);
        PortfolioResponseMapper mapper = mock(PortfolioResponseMapper.class);
        PublicContent content = mock(PublicContent.class);
        PublicContentResponse response = mock(PublicContentResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PublicContentController(
                service, mapper, "POSTGRESQL", "ENABLED",
                new ConversationProviderAccess(true))
                .getPublicContent();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content), availability.capture());
        assertThat(availability.getValue().getStatus())
                .isEqualTo(AgentAvailabilityResponse.Status.AVAILABLE);
        assertThat(availability.getValue().getFreeTextSemanticRouting())
                .isEqualTo(
                        AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE);
    }

    @Test
    void persistentStateKeepsDeterministicAgentAvailableWhenFreeTextIsDisabled() {
        PortfolioService service = mock(PortfolioService.class);
        PortfolioResponseMapper mapper = mock(PortfolioResponseMapper.class);
        PublicContent content = mock(PublicContent.class);
        PublicContentResponse response = mock(PublicContentResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PublicContentController(
                service, mapper, "POSTGRESQL", "DISABLED",
                new ConversationProviderAccess(true))
                .getPublicContent();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content),
                availability.capture());
        assertThat(availability.getValue().getStatus())
                .isEqualTo(AgentAvailabilityResponse.Status.AVAILABLE);
        assertThat(availability.getValue().getFreeTextSemanticRouting())
                .isEqualTo(
                        AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
    }

    @Test
    void providerPrivacyGateDisablesFreeTextWithoutDisablingDeterministicTurns() {
        PortfolioService service = mock(PortfolioService.class);
        PortfolioResponseMapper mapper = mock(PortfolioResponseMapper.class);
        PublicContent content = mock(PublicContent.class);
        PublicContentResponse response = mock(PublicContentResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PublicContentController(
                service, mapper, "POSTGRESQL", "ENABLED",
                new ConversationProviderAccess(false))
                .getPublicContent();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPublicContentResponse(
                org.mockito.ArgumentMatchers.eq(content),
                availability.capture());
        assertThat(availability.getValue().getStatus())
                .isEqualTo(AgentAvailabilityResponse.Status.AVAILABLE);
        assertThat(availability.getValue().getFreeTextSemanticRouting())
                .isEqualTo(
                        AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
    }
}
