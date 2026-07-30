package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicSnapshotFingerprintFactoryTest {
    @Test
    void buildsDeterministicSemanticFingerprintFromRealPublicBundle() throws Exception {
        Set<String> names = Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<>();
        Path root = Path.of("src/main/resources/public-data/bundle");
        for (String name : names) {
            files.put(name, Files.readAllBytes(root.resolve(name)));
        }
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RuntimeContentSnapshot snapshot = new PublicBundleLoader(
                mapper, new PortfolioSnapshotValidator(), Clock.systemUTC()).load(files);
        PublicSnapshotFingerprintFactory factory = new PublicSnapshotFingerprintFactory(mapper);

        PublicSnapshotFingerprint first = factory.create(snapshot);
        PublicSnapshotFingerprint second = factory.create(snapshot);

        assertThat(first.getSemanticCounts()).containsEntry("PROJECT", 5)
                .containsEntry("CASE", 49).containsEntry("COLLECTION", 3)
                .containsEntry("CLAIM", 79).containsEntry("EVIDENCE", 59)
                .containsEntry("CLAIM_EVIDENCE_LINK", 79)
                .containsEntry("RETRIEVAL_DOCUMENT", 79);
        assertThat(first.getRelationships()).isNotEmpty().isEqualTo(second.getRelationships());
        assertThat(first.getCanonicalHashes()).isNotEmpty().isEqualTo(second.getCanonicalHashes());
    }
}
