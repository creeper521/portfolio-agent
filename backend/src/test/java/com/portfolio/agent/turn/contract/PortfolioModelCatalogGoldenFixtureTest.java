package com.portfolio.agent.turn.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogEntry;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioModelCatalogGoldenFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void backendSerializerMatchesTheCredentialFreeCatalogFixture() throws Exception {
        ModelCatalogSnapshot catalog = mock(ModelCatalogSnapshot.class);
        when(catalog.getSnapshotVersion()).thenReturn("catalog-public-v4");
        when(catalog.getEntries()).thenReturn(List.of(
                entry("glm-4-7-flash", "GLM-4.7-Flash", 10),
                entry("qwen-3-7-flash", "Qwen3.7-Flash", 20)));
        when(catalog.getDefaultModelSelection()).thenReturn(
                new ModelCatalogDefaultSelection(
                        ModelCatalogDefaultSelection.Kind.MODEL,
                        "glm-4-7-flash", "glm-4-7-flash-v4"));
        AgentAvailabilityResponse availability = AgentAvailabilityResponse.available(
                AgentAvailabilityResponse.FreeTextSemanticRouting.AVAILABLE, catalog);
        JsonNode fixture = mapper.readTree(repositoryRoot().resolve(
                "contracts/agent-turn/portfolio-model-catalog.json").toFile());
        JsonNode serialized = mapper.valueToTree(availability);

        assertThat(serialized).isEqualTo(fixture);
        assertThat(fixture.toString()).doesNotContain(
                "endpoint", "apiKey", "credential", "protocolProfile",
                "descriptorFingerprint", "maxOutputTokens");
    }

    private ModelCatalogEntry entry(
            String modelRef, String displayName, int displayOrder) {
        return new ModelCatalogEntry(
                modelRef, displayName, displayOrder,
                "qwen-3-7-flash".equals(modelRef)
                        ? "qwen-3-7-flash-v6" : modelRef + "-v4",
                Set.of(ModelCapability.TURN_INTERPRETATION,
                        ModelCapability.GENERAL_KNOWLEDGE));
    }

    private Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("backend"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("backend"))) {
            return parent;
        }
        throw new IOException("repository root not found from " + current);
    }
}
