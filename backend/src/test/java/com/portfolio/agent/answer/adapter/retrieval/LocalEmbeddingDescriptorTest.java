package com.portfolio.agent.answer.adapter.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEmbeddingDescriptorTest {

    @Test
    void pinsImmutableRevisionAndEveryRequiredFileHash() throws Exception {
        InputStream stream = getClass().getResourceAsStream(
                "/embedding-models/bge-small-zh-v1.5-int8.json");

        assertThat(stream).isNotNull();
        JsonNode descriptor = new ObjectMapper().readTree(stream);
        assertThat(descriptor.path("repository").asText())
                .isEqualTo("onnx-community/bge-small-zh-v1.5-ONNX");
        assertThat(descriptor.path("revision").asText())
                .matches("[0-9a-f]{40}");
        assertThat(descriptor.path("dimension").asInt()).isEqualTo(512);
        assertThat(descriptor.path("maxTokens").asInt()).isEqualTo(256);
        assertThat(descriptor.path("license").asText()).isEqualTo("MIT");

        Set<String> names = new HashSet<>();
        for (JsonNode file : descriptor.path("files")) {
            names.add(file.path("path").asText());
            assertThat(file.path("sha256").asText()).matches("[0-9a-f]{64}");
            assertThat(file.path("size").asLong()).isPositive();
        }
        assertThat(names).containsExactlyInAnyOrder(
                "onnx/model_quantized.onnx", "onnx/model_quantized.onnx_data",
                "tokenizer.json", "tokenizer_config.json", "config.json");
    }

    @Test
    void verifierRejectsAnyModelArtifactWhoseBytesDoNotMatchThePinnedDescriptor(
            @org.junit.jupiter.api.io.TempDir Path temporary
    ) throws Exception {
        Path installed = projectRoot().resolve("runtime-models/bge-small-zh-v1.5");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(installed));
        Path copied = temporary.resolve("model");
        try (java.util.stream.Stream<Path> paths = Files.walk(installed)) {
            for (Path source : paths.toList()) {
                Path target = copied.resolve(installed.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        Files.writeString(copied.resolve("tokenizer.json"), "tampered");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> new LocalEmbeddingArtifactVerifier().verify(copied)))
                .isInstanceOf(com.portfolio.agent.answer.service.LocalEmbeddingFailureException.class)
                .hasMessage("LOCAL_MODEL_ARTIFACT_MISMATCH");
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend")) ? current : current.getParent();
    }
}
