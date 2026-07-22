package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.portfolio.domain.BundleCounts;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ReleaseManifest;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.ActiveBundleLocator;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class C2ReleaseFixtureBuilderTest {

    @TempDir
    Path temporary;

    @Test
    void buildsAClosedSevenFileReleaseFromExactCanonicalPayloadBytes() throws Exception {
        Path projectRoot = projectRoot();
        Path model = projectRoot.resolve("runtime-models/bge-small-zh-v1.5");
        Assumptions.assumeTrue(Files.isRegularFile(
                model.resolve("onnx/model_quantized.onnx")));
        String configuredRoot = System.getProperty("portfolio.c2.fixtureRoot", "").strip();
        Path releaseRoot = configuredRoot.isEmpty()
                ? temporary.resolve("release-root")
                : Path.of(configuredRoot).toAbsolutePath().normalize();
        Files.createDirectories(releaseRoot);

        Path sourceBundle = projectRoot.resolve(
                "backend/src/main/resources/public-data/bundle");
        Path artifacts = releaseRoot.resolve("retrieval-artifacts");
        RetrievalBundleCompilerCli.compile(
                sourceBundle.resolve("portfolio.json"), model, artifacts,
                LocalDate.of(2026, 7, 21));

        ObjectMapper mapper = mapper();
        byte[] portfolioBytes = Files.readAllBytes(sourceBundle.resolve("portfolio.json"));
        byte[] presentationBytes = Files.readAllBytes(
                sourceBundle.resolve("presentation.json"));
        PortfolioSnapshot snapshot = mapper.readValue(portfolioBytes, PortfolioSnapshot.class);
        RetrievalManifest retrieval = mapper.readValue(
                Files.readAllBytes(artifacts.resolve("retrieval-manifest.json")),
                RetrievalManifest.class);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("portfolio.json", portfolioBytes);
        files.put("presentation.json", presentationBytes);
        files.put("rag-documents.jsonl",
                Files.readAllBytes(artifacts.resolve("rag-documents.jsonl")));
        files.put("keyword-index.json",
                Files.readAllBytes(artifacts.resolve("keyword-index.json")));
        files.put("vector-index.bin",
                Files.readAllBytes(artifacts.resolve("vector-index.bin")));

        String candidateHash = BundleHashCalculator.candidatePayloadHash(files);
        BundleCounts counts = new BundleCounts(
                snapshot.getProjects().size(), snapshot.getClaims().size(),
                snapshot.getEvidence().size(), snapshot.getClaimEvidenceLinks().size(),
                snapshot.getTimeline().size(), snapshot.getQuestions().size());
        OffsetDateTime publishedAt = OffsetDateTime.parse("2026-07-21T16:30:00+08:00");
        ReleaseManifest manifest = new ReleaseManifest(
                "2.0", snapshot.getContentVersion(), publishedAt, publishedAt,
                "0.1.0", "portfolio.json", "presentation.json",
                "TEST-C2-APPROVAL-FIXTURE",
                BundleHashCalculator.sha256(
                        ("TEST-C2-APPROVAL-FIXTURE\0" + candidateHash)
                                .getBytes(StandardCharsets.UTF_8)),
                candidateHash, "checksums.json", counts, retrieval);
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        Map<String, Object> checksums = new LinkedHashMap<>();
        checksums.put("schemaVersion", "2.0");
        checksums.put("contentVersion", snapshot.getContentVersion());
        Map<String, String> hashes = new java.util.TreeMap<>();
        files.forEach((name, bytes) -> hashes.put(name, BundleHashCalculator.sha256(bytes)));
        checksums.put("files", hashes);
        byte[] checksumBytes = mapper.writeValueAsBytes(checksums);
        files.put("manifest.json", manifestBytes);
        files.put("checksums.json", checksumBytes);

        Path versions = Files.createDirectories(releaseRoot.resolve("versions"));
        Path version = Files.createDirectories(versions.resolve(snapshot.getContentVersion()));
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Files.write(version.resolve(entry.getKey()), entry.getValue());
        }
        Files.writeString(releaseRoot.resolve("active"), snapshot.getContentVersion());

        RuntimeContentSnapshot loaded = new ActiveBundleLocator().load(
                releaseRoot,
                new PublicBundleLoader(
                        mapper, new PortfolioSnapshotValidator(), Clock.systemUTC()));
        assertThat(loaded.getRetrievalContent()).isPresent();
        assertThat(loaded.getRuntimeBundleHash())
                .isEqualTo(BundleHashCalculator.runtimeBundleHash(
                        manifestBytes, checksumBytes));
        assertThat(Files.readAllBytes(version.resolve("portfolio.json")))
                .isEqualTo(portfolioBytes);
    }

    private ObjectMapper mapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend"))
                ? current
                : current.getParent();
    }
}
