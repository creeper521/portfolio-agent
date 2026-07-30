package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.portfolio.domain.ReleaseManifest;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PublicBundleVerificationCli {

    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "schemaVersion", "contentVersion", "publishedAt", "builtAt",
            "minimumApplicationVersion", "factsFile", "presentationFile",
            "approvalId", "approvalDigest", "candidatePayloadHash", "ledgerHash",
            "checksumsFile", "counts", "retrieval"
    );

    private PublicBundleVerificationCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args == null || args.length != 1
                    || args[0] == null || args[0].isBlank()) {
                throw new IllegalArgumentException("one bundle directory is required");
            }
            ObjectMapper mapper = mapper();
            Map<String, byte[]> files = new PublicBundleDirectoryReader()
                    .read(Path.of(args[0]));
            byte[] originalManifest = files.get("manifest.json");
            ObjectNode manifestNode = readManifestEnvelope(mapper, originalManifest);
            String ledgerHash = requiredHash(manifestNode, "ledgerHash");

            RuntimeContentSnapshot snapshot = new PublicBundleLoader(
                    mapper,
                    new PortfolioSnapshotValidator(),
                    Clock.systemUTC()
            ).load(files);
            ReleaseManifest manifest = mapper.treeToValue(
                    manifestNode, ReleaseManifest.class);
            RetrievalManifest retrieval = manifest.getRetrieval();
            if (retrieval == null) {
                throw new IllegalArgumentException("retrieval manifest is required");
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("schemaVersion", snapshot.getSchemaVersion());
            summary.put("contentVersion", snapshot.getContentVersion());
            summary.put("candidatePayloadHash", manifest.getCandidatePayloadHash());
            summary.put("ledgerHash", ledgerHash);
            summary.put("runtimeBundleHash", BundleHashCalculator.runtimeBundleHash(
                    originalManifest, files.get("checksums.json")));
            summary.put("retrievalPolicyVersion", retrieval.getRetrievalPolicyVersion());
            summary.put("embeddingModelId", retrieval.getEmbeddingModelId());
            summary.put("embeddingArtifactSha256",
                    retrieval.getEmbeddingArtifactSha256());
            summary.put("dimension", retrieval.getDimension());
            summary.put("chunkCount", retrieval.getChunkCount());
            summary.put("projects", snapshot.getProjects().size());
            summary.put("cases", snapshot.getCases().size());
            summary.put("claims", snapshot.getClaims().size());
            summary.put("evidenceCount", snapshot.getApprovedEvidence().size());
            out.println(mapper.writeValueAsString(summary));
            return 0;
        } catch (IOException | RuntimeException exception) {
            err.println("PUBLIC_BUNDLE_VERIFICATION_FAILED");
            return 1;
        }
    }

    private static ObjectNode readManifestEnvelope(
            ObjectMapper mapper,
            byte[] source
    )
            throws IOException {
        JsonNode value = mapper.readTree(source);
        if (!(value instanceof ObjectNode manifest)) {
            throw new IllegalArgumentException("manifest field set is invalid");
        }
        Set<String> fields = new HashSet<>();
        manifest.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(MANIFEST_FIELDS)) {
            throw new IllegalArgumentException("manifest field set is invalid");
        }
        return manifest;
    }

    private static String requiredHash(ObjectNode manifest, String name) {
        JsonNode value = manifest.get(name);
        if (value == null || !value.isTextual()
                || !value.textValue().matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("manifest identity is invalid");
        }
        return value.textValue();
    }

    private static ObjectMapper mapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }
}
