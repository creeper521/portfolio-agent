package com.portfolio.agent.answer.adapter.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LocalEmbeddingArtifactVerifier {
    private static final String DESCRIPTOR_RESOURCE =
            "/embedding-models/bge-small-zh-v1.5-int8.json";

    public LocalEmbeddingArtifact descriptor() {
        try (InputStream input = getClass().getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (input == null) {
                throw failure("LOCAL_MODEL_DESCRIPTOR_MISSING");
            }
            byte[] bytes = input.readAllBytes();
            JsonNode descriptor = new ObjectMapper().readTree(bytes);
            requireDescriptor(descriptor);
            return toArtifact(descriptor, sha256(bytes));
        } catch (LocalEmbeddingFailureException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
        }
    }

    public LocalEmbeddingArtifact verify(Path modelDirectory) {
        if (modelDirectory == null || !Files.isDirectory(modelDirectory)) {
            throw failure("LOCAL_MODEL_DIRECTORY_INVALID");
        }
        try (InputStream input = getClass().getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (input == null) {
                throw failure("LOCAL_MODEL_DESCRIPTOR_MISSING");
            }
            byte[] bytes = input.readAllBytes();
            JsonNode descriptor = new ObjectMapper().readTree(bytes);
            requireDescriptor(descriptor);
            Path root = modelDirectory.toAbsolutePath().normalize();
            for (JsonNode file : descriptor.path("files")) {
                String relativeName = requiredText(file, "path");
                Path candidate = root.resolve(relativeName).normalize();
                if (!candidate.startsWith(root)
                        || !Files.isRegularFile(candidate)
                        || Files.size(candidate) != file.path("size").asLong()
                        || !sha256(candidate).equals(
                                "sha256:" + requiredText(file, "sha256"))) {
                    throw failure("LOCAL_MODEL_ARTIFACT_MISMATCH");
                }
            }
            return toArtifact(descriptor, sha256(bytes));
        } catch (LocalEmbeddingFailureException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure("LOCAL_MODEL_ARTIFACT_MISMATCH");
        }
    }

    private void requireDescriptor(JsonNode descriptor) {
        if (!"1.0".equals(requiredText(descriptor, "schemaVersion"))
                || descriptor.path("dimension").asInt() <= 0
                || descriptor.path("maxTokens").asInt() <= 0
                || descriptor.path("intraOpNumThreads").asInt() <= 0
                || descriptor.path("interOpNumThreads").asInt() <= 0
                || !descriptor.path("files").isArray()
                || descriptor.path("files").isEmpty()) {
            throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
        }
        requiredText(descriptor, "modelId");
        requiredText(descriptor, "queryInstruction");
        for (JsonNode file : descriptor.path("files")) {
            String path = requiredText(file, "path");
            String hash = requiredText(file, "sha256");
            if (Path.of(path).isAbsolute() || path.contains("..")
                    || file.path("size").asLong() <= 0
                    || !hash.matches("[0-9a-f]{64}")) {
                throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
            }
        }
    }

    private LocalEmbeddingArtifact toArtifact(JsonNode descriptor, String descriptorHash) {
        return new LocalEmbeddingArtifact(requiredText(descriptor, "modelId"), descriptorHash,
                descriptor.path("dimension").asInt(), descriptor.path("maxTokens").asInt(),
                requiredText(descriptor, "queryInstruction"),
                descriptor.path("intraOpNumThreads").asInt(),
                descriptor.path("interOpNumThreads").asInt());
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw failure("LOCAL_MODEL_DESCRIPTOR_INVALID");
        }
        return value;
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(byte[] bytes) {
        return "sha256:" + HexFormat.of().formatHex(digest().digest(bytes));
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalEmbeddingFailureException failure(String code) {
        return new LocalEmbeddingFailureException(code);
    }
}
