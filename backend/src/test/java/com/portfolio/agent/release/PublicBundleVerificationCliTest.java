package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import com.portfolio.agent.portfolio.repository.file.KeywordIndexFile;
import com.portfolio.agent.portfolio.repository.file.VectorIndexCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicBundleVerificationCliTest {

    @TempDir
    Path temporary;

    private final ObjectMapper mapper = new ObjectMapper();
    private Path bundle;

    @BeforeEach
    void prepareBundle() throws Exception {
        bundle = Files.createDirectory(temporary.resolve("external-bundle"));
        writeSevenFileBundle(projectRoot().resolve(
                "backend/src/main/resources/public-data/bundle"), bundle);
    }

    @Test
    void verifiesExactExternalBundleAndPrintsOnlyStablePublicIdentities() {
        RunResult result = run(bundle.toString());

        assertThat(result.exitCode).isZero();
        assertThat(result.err).isEmpty();
        assertThat(result.out).contains(
                "\"contentVersion\":\"2026-07-27.1\"",
                "\"candidatePayloadHash\":\"sha256:",
                "\"ledgerHash\":\"sha256:",
                "\"runtimeBundleHash\":\"sha256:",
                "\"chunkCount\":1",
                "\"projects\":7");
        assertThat(result.out)
                .doesNotContain(bundle.toAbsolutePath().toString())
                .doesNotContain(System.getProperty("user.name"))
                .doesNotContain("Workspace")
                .doesNotContain("DecisionLedger")
                .doesNotContain("Evidence");
    }

    @Test
    void acceptsExactlyOneDirectoryArgument() {
        assertThat(run().exitCode).isEqualTo(1);
        assertThat(run(bundle.toString(), bundle.toString()).exitCode).isEqualTo(1);
        assertThat(run(temporary.resolve("missing").toString()).exitCode).isEqualTo(1);
    }

    @Test
    void neverFallsBackWhenExternalBundleIsLegacyOrIncomplete() throws Exception {
        Files.delete(bundle.resolve("vector-index.bin"));

        RunResult result = run(bundle.toString());

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.out).isEmpty();
        assertThat(result.err).contains("PUBLIC_BUNDLE_VERIFICATION_FAILED")
                .doesNotContain(bundle.toString());
    }

    @Test
    void rejectsExtraFilesAndSymbolicLinks() throws Exception {
        Files.writeString(bundle.resolve("approval.json"), "{}");
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);
        Files.delete(bundle.resolve("approval.json"));

        Path link = temporary.resolve("bundle-link");
        try {
            Files.createSymbolicLink(link, bundle);
            assertThat(run(link.toString()).exitCode).isEqualTo(1);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            assertThat(Files.isDirectory(bundle)).isTrue();
        }
    }

    @Test
    void rejectsChecksumCandidateReferenceAndRetrievalIdentityMismatches()
            throws Exception {
        Files.writeString(bundle.resolve("portfolio.json"), "{}");
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);

        recreateBundle();
        ObjectNode manifest = object(bundle.resolve("manifest.json"));
        manifest.put("candidatePayloadHash", "sha256:" + "0".repeat(64));
        write(bundle.resolve("manifest.json"), manifest);
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);

        recreateBundle();
        byte[] rag = Files.readAllBytes(bundle.resolve("rag-documents.jsonl"));
        byte[] changedRag = new String(rag, StandardCharsets.UTF_8)
                .replace("chunk-sql-audit-delivery", "chunk-reference-mismatch")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(bundle.resolve("rag-documents.jsonl"), changedRag);
        refreshChecksumsAndManifest(changedRag, null);
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);

        recreateBundle();
        ObjectNode keyword = object(bundle.resolve("keyword-index.json"));
        keyword.put("normalizationVersion", "wrong-normalization");
        write(bundle.resolve("keyword-index.json"), keyword);
        refreshChecksumsAndManifest(null, null);
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);
    }

    @Test
    void rejectsMissingOrMalformedGovernanceLedgerIdentity() throws Exception {
        ObjectNode missing = object(bundle.resolve("manifest.json"));
        missing.remove("ledgerHash");
        write(bundle.resolve("manifest.json"), missing);
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);

        recreateBundle();
        ObjectNode malformed = object(bundle.resolve("manifest.json"));
        malformed.put("ledgerHash", "sha256:not-a-ledger-hash");
        write(bundle.resolve("manifest.json"), malformed);
        assertThat(run(bundle.toString()).exitCode).isEqualTo(1);
    }

    private void recreateBundle() throws Exception {
        try (java.util.stream.Stream<Path> entries = Files.list(bundle)) {
            for (Path entry : entries.toList()) {
                Files.delete(entry);
            }
        }
        writeSevenFileBundle(projectRoot().resolve(
                "backend/src/main/resources/public-data/bundle"), bundle);
    }

    private void refreshChecksumsAndManifest(byte[] changedRag, String ignored)
            throws Exception {
        ObjectNode manifest = object(bundle.resolve("manifest.json"));
        byte[] rag = changedRag == null
                ? Files.readAllBytes(bundle.resolve("rag-documents.jsonl"))
                : changedRag;
        ((ObjectNode) manifest.get("retrieval"))
                .put("chunkSetHash", BundleHashCalculator.sha256(rag));
        Map<String, byte[]> payload = payloadFiles();
        manifest.put("candidatePayloadHash",
                BundleHashCalculator.candidatePayloadHash(payload));
        write(bundle.resolve("manifest.json"), manifest);

        ObjectNode checksums = object(bundle.resolve("checksums.json"));
        ObjectNode hashes = (ObjectNode) checksums.get("files");
        for (Map.Entry<String, byte[]> entry : payload.entrySet()) {
            hashes.put(entry.getKey(), BundleHashCalculator.sha256(entry.getValue()));
        }
        write(bundle.resolve("checksums.json"), checksums);
    }

    private Map<String, byte[]> payloadFiles() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin")) {
            files.put(name, Files.readAllBytes(bundle.resolve(name)));
        }
        return files;
    }

    private ObjectNode object(Path file) throws Exception {
        return (ObjectNode) mapper.readTree(Files.readAllBytes(file));
    }

    private void write(Path file, ObjectNode value) throws Exception {
        Files.write(file, mapper.writeValueAsBytes(value));
    }

    private RunResult run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = PublicBundleVerificationCli.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new RunResult(
                exitCode,
                out.toString(StandardCharsets.UTF_8).trim(),
                err.toString(StandardCharsets.UTF_8).trim());
    }

    private void writeSevenFileBundle(Path source, Path target) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("portfolio.json", Files.readAllBytes(source.resolve("portfolio.json")));
        files.put("presentation.json",
                Files.readAllBytes(source.resolve("presentation.json")));
        byte[] rag = ("{\"chunkId\":\"chunk-sql-audit-delivery\","
                + "\"contentVersion\":\"2026-07-27.1\","
                + "\"projectSlugs\":[\"sql-audit\"],\"caseSlugs\":[],"
                + "\"claimIds\":[\"claim-sql-audit-delivered\"],"
                + "\"text\":\"SQL audit delivered\","
                + "\"topics\":[\"DELIVERY\"],\"validFrom\":\"2026-07-01\","
                + "\"validUntil\":null,\"contentHash\":\"sha256:chunk\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        files.put("rag-documents.jsonl", rag);
        KeywordIndexFile keyword = new KeywordIndexFile(
                "keyword-index-v1", "nfkc-bigram-v1", 1, 3.0,
                List.of(new KeywordIndexFile.DocumentEntry(
                        "chunk-sql-audit-delivery", 3,
                        Map.of("sql", 1, "audit", 1))),
                Map.of("sql", 1, "audit", 1));
        files.put("keyword-index.json", mapper.writeValueAsBytes(keyword));
        float[] vector = new float[512];
        vector[0] = 1.0f;
        files.put("vector-index.bin", new VectorIndexCodec().encode(
                Map.of("chunk-sql-audit-delivery", vector), 512));

        ObjectNode manifest = (ObjectNode) mapper.readTree(
                Files.readAllBytes(source.resolve("manifest.json")));
        ObjectNode retrieval = mapper.createObjectNode();
        retrieval.put("strategyVersion", "hybrid-rag-v1");
        retrieval.put("normalizationVersion", "nfkc-bigram-v1");
        retrieval.put("retrievalPolicyVersion", "retrieval-policy-v1");
        retrieval.put("embeddingModelId", "BAAI/bge-small-zh-v1.5");
        retrieval.put("embeddingArtifactSha256", "sha256:model");
        retrieval.put("dimension", 512);
        retrieval.put("documentMaxTokens", 256);
        retrieval.put("vectorNormalization", "L2");
        retrieval.put("similarity", "COSINE");
        retrieval.put("chunkCount", 1);
        retrieval.put("chunkSetHash", BundleHashCalculator.sha256(rag));
        retrieval.put("keywordIndexFormatVersion", "keyword-index-v1");
        retrieval.put("vectorIndexFormatVersion", "vector-index-v1");
        manifest.set("retrieval", retrieval);
        manifest.put("ledgerHash", "sha256:" + "1".repeat(64));
        manifest.put("candidatePayloadHash",
                BundleHashCalculator.candidatePayloadHash(files));
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);

        ObjectNode checksums = mapper.createObjectNode();
        checksums.put("schemaVersion", manifest.path("schemaVersion").asText());
        checksums.put("contentVersion", manifest.path("contentVersion").asText());
        ObjectNode hashes = mapper.createObjectNode();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            hashes.put(entry.getKey(), BundleHashCalculator.sha256(entry.getValue()));
        }
        checksums.set("files", hashes);
        files.put("manifest.json", manifestBytes);
        files.put("checksums.json", mapper.writeValueAsBytes(checksums));
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Files.write(target.resolve(entry.getKey()), entry.getValue());
        }
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend"))
                ? current
                : current.getParent();
    }

    private static final class RunResult {
        private final int exitCode;
        private final String out;
        private final String err;

        private RunResult(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }
}
