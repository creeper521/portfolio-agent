package com.portfolio.agent.portfolio.controller;

import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioSnapshotResponse;
import com.portfolio.agent.portfolio.mapper.PortfolioResponseMapper;
import com.portfolio.agent.portfolio.service.PortfolioService;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogEntry;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.infrastructure.AgentRuntimeReadiness;
import com.portfolio.agent.turn.state.configuration.ConversationContextProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.List;
import java.util.Set;

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
                        OperationMode.ENABLED), emptyCatalog())
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
                        OperationMode.ENABLED), readyCatalog())
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
                        OperationMode.DISABLED), readyCatalog())
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
    void emptyCatalogDisablesFreeTextWithoutDisablingDeterministicTurns() {
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
                        OperationMode.ENABLED), emptyCatalog())
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
    void projectsTheCompleteSafeModelCatalogWithoutInfrastructureFields() throws Exception {
        ModelCatalogSnapshot catalog = mock(ModelCatalogSnapshot.class);
        when(catalog.getSnapshotVersion()).thenReturn("catalog-public-v3");
        when(catalog.getEntries()).thenReturn(List.of(
                new ModelCatalogEntry(
                        "glm-4-7-flash", "GLM-4.7-Flash", 10,
                        "glm-4-7-flash-v1", Set.of(
                        ModelCapability.TURN_INTERPRETATION,
                        ModelCapability.GENERAL_KNOWLEDGE)),
                new ModelCatalogEntry(
                        "qwen-3-7-flash", "Qwen3.7-Flash", 20,
                        "qwen-3-7-flash-v1", Set.of(
                        ModelCapability.TURN_INTERPRETATION,
                        ModelCapability.GENERAL_KNOWLEDGE))));
        when(catalog.getDefaultModelSelection()).thenReturn(
                new ModelCatalogDefaultSelection(
                        ModelCatalogDefaultSelection.Kind.MODEL,
                        "glm-4-7-flash", "glm-4-7-flash-v1"));

        AgentAvailabilityResponse response = AgentAvailabilityResponse.available(
                AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE, catalog);
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(response);

        assertThat(response.getModelCatalogVersion()).isEqualTo("catalog-public-v3");
        assertThat(response.getSelectableModels())
                .extracting(AgentAvailabilityResponse.SelectableModel::getModelRef)
                .containsExactly("glm-4-7-flash", "qwen-3-7-flash");
        assertThat(response.getDefaultModelSelection().getKind())
                .isEqualTo(AgentAvailabilityResponse.SelectionKind.MODEL);
        assertThat(json).doesNotContain(
                "endpoint", "apiKey", "credential", "protocolProfile",
                "descriptorFingerprint", "maxOutputTokens", "displayOrder", "capabilities");
    }

    @Test
    void emptyCatalogForcesNoneDefaultAndDisablesFreeTextRouting() {
        AgentAvailabilityResponse response = AgentAvailabilityResponse.available(
                AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE, emptyCatalog());

        assertThat(response.getFreeTextSemanticRouting())
                .isEqualTo(AgentAvailabilityResponse.FreeTextSemanticRouting.DISABLED);
        assertThat(response.getDefaultModelSelection().getKind())
                .isEqualTo(AgentAvailabilityResponse.SelectionKind.NONE);
        assertThat(response.getDefaultModelSelection().getModelRef()).isNull();
        assertThat(response.getSelectableModels()).isEmpty();
    }

    @Test
    void singleReadyModelCanKeepNoneAsTheExplicitDefault() {
        ModelCatalogSnapshot catalog = mock(ModelCatalogSnapshot.class);
        when(catalog.getSnapshotVersion()).thenReturn("catalog-one");
        when(catalog.getEntries()).thenReturn(List.of(new ModelCatalogEntry(
                "qwen-3-7-flash", "Qwen3.7-Flash", 20,
                "qwen-3-7-flash-v1", Set.of(ModelCapability.TURN_INTERPRETATION))));
        when(catalog.getDefaultModelSelection()).thenReturn(
                ModelCatalogDefaultSelection.none());

        AgentAvailabilityResponse response = AgentAvailabilityResponse.available(
                AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE, catalog);

        assertThat(response.getSelectableModels()).hasSize(1);
        assertThat(response.getDefaultModelSelection().getKind())
                .isEqualTo(AgentAvailabilityResponse.SelectionKind.NONE);
        assertThat(response.getFreeTextSemanticRouting())
                .isEqualTo(AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE);
    }

    private ModelCatalogSnapshot emptyCatalog() {
        return ModelCatalogSnapshot.empty();
    }

    private ModelCatalogSnapshot readyCatalog() {
        ModelCatalogSnapshot catalog = mock(ModelCatalogSnapshot.class);
        when(catalog.getSnapshotVersion()).thenReturn("catalog-ready");
        when(catalog.getEntries()).thenReturn(List.of(new ModelCatalogEntry(
                "glm-4-7-flash", "GLM-4.7-Flash", 10,
                "glm-4-7-flash-v1", Set.of(ModelCapability.TURN_INTERPRETATION))));
        when(catalog.getDefaultModelSelection()).thenReturn(
                new ModelCatalogDefaultSelection(
                        ModelCatalogDefaultSelection.Kind.MODEL,
                        "glm-4-7-flash", "glm-4-7-flash-v1"));
        return catalog;
    }

    private AgentRuntimeReadiness readiness(
            ConversationContextProperties.Mode contextMode,
            OperationMode operationMode) {
        ModelOperationPolicy turnPolicy = operationMode == OperationMode.ENABLED
                ? new ModelOperationPolicy(
                ModelOperation.TURN_INTERPRETATION, operationMode,
                GoalProposalCodec.SCHEMA_VERSION, 1600,
                java.time.Duration.ofSeconds(8))
                : new ModelOperationPolicy(
                ModelOperation.TURN_INTERPRETATION, operationMode,
                null, 0, null);
        return new AgentRuntimeReadiness(
                contextMode,
                new ModelOperationPolicyRegistry(Map.of(
                        ModelOperation.TURN_INTERPRETATION, turnPolicy)),
                com.portfolio.agent.infrastructure.model.structured
                        .StructuredModelTestFixtures.contracts());
    }
}
