package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioSnapshotResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.infrastructure.model.policy.ConversationProviderAccess;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderKind;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.infrastructure.AgentRuntimeReadiness;
import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioControllerAvailabilityTest {
    @Test
    void disabledStateProjectsUnavailableWithoutDependingOnTurnTypes() {
        PortfolioService service = mock(PortfolioService.class);
        PortfolioResponseMapper mapper = mock(PortfolioResponseMapper.class);
        PublicContent content = mock(PublicContent.class);
        PortfolioSnapshotResponse response = mock(PortfolioSnapshotResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPortfolioSnapshotResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PortfolioController(
                service, mapper, readiness(
                        ConversationContextProperties.Mode.DISABLED,
                        OperationMode.ENABLED, true))
                .getPortfolioSnapshot();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPortfolioSnapshotResponse(
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
        PortfolioSnapshotResponse response = mock(PortfolioSnapshotResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPortfolioSnapshotResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PortfolioController(
                service, mapper, readiness(
                        ConversationContextProperties.Mode.POSTGRESQL,
                        OperationMode.ENABLED, true))
                .getPortfolioSnapshot();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPortfolioSnapshotResponse(
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
        PortfolioSnapshotResponse response = mock(PortfolioSnapshotResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPortfolioSnapshotResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PortfolioController(
                service, mapper, readiness(
                        ConversationContextProperties.Mode.POSTGRESQL,
                        OperationMode.DISABLED, true))
                .getPortfolioSnapshot();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPortfolioSnapshotResponse(
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
        PortfolioSnapshotResponse response = mock(PortfolioSnapshotResponse.class);
        when(service.getPublicContent()).thenReturn(content);
        when(mapper.toPortfolioSnapshotResponse(
                org.mockito.ArgumentMatchers.eq(content),
                org.mockito.ArgumentMatchers.any(AgentAvailabilityResponse.class)))
                .thenReturn(response);

        new PortfolioController(
                service, mapper, readiness(
                        ConversationContextProperties.Mode.POSTGRESQL,
                        OperationMode.ENABLED, false))
                .getPortfolioSnapshot();

        ArgumentCaptor<AgentAvailabilityResponse> availability =
                ArgumentCaptor.forClass(AgentAvailabilityResponse.class);
        verify(mapper).toPortfolioSnapshotResponse(
                org.mockito.ArgumentMatchers.eq(content),
                availability.capture());
        assertThat(availability.getValue().getStatus())
                .isEqualTo(AgentAvailabilityResponse.Status.AVAILABLE);
        assertThat(availability.getValue().getFreeTextSemanticRouting())
                .isEqualTo(
                        AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
    }

    private AgentRuntimeReadiness readiness(
            ConversationContextProperties.Mode contextMode,
            OperationMode operationMode,
            boolean providerAllowed) {
        ModelOperationPolicy turnPolicy = operationMode == OperationMode.ENABLED
                ? new ModelOperationPolicy(
                ModelOperation.TURN_INTERPRETATION, operationMode,
                ModelProviderKind.DEEPSEEK_V4_FLASH.name(),
                GoalProposalCodec.SCHEMA_VERSION)
                : new ModelOperationPolicy(
                ModelOperation.TURN_INTERPRETATION, operationMode, null, null);
        return new AgentRuntimeReadiness(
                contextMode, new ConversationProviderAccess(providerAllowed),
                new ModelOperationPolicyRegistry(Map.of(
                        ModelOperation.TURN_INTERPRETATION, turnPolicy)),
                ModelProviderKind.DEEPSEEK_V4_FLASH);
    }
}
