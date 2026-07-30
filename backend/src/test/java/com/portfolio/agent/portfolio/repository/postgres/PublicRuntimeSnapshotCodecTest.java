package com.portfolio.agent.portfolio.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicRuntimeSnapshotCodecTest {

    @Test
    void roundTripsTheCompleteRuntimeSnapshotIncludingRetrievalIndexesAndVectors() throws Exception {
        RuntimeContentSnapshot source = actualBundleSnapshot();
        PublicRuntimeSnapshotCodec codec =
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());

        EncodedRuntimeSnapshot encoded = codec.encode(source);
        RuntimeContentSnapshot decoded = codec.decode(encoded.getPayload());

        assertThat(decoded).usingRecursiveComparison().isEqualTo(source);
        assertThat(encoded.getChecksum()).matches("[0-9a-f]{64}");
        assertThat(encoded.getPayload()).doesNotContain("\\\\", "D:\\\\", "governance");
    }

    @Test
    void checksumIsStableWhenJsonbChangesWhitespaceAndObjectPropertyOrder() {
        PublicRuntimeSnapshotCodec codec =
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());

        assertThat(codec.checksum("{\"z\":1,\"nested\":{\"b\":2,\"a\":3}}"))
                .isEqualTo(codec.checksum("""
                        { "nested": { "a": 3, "b": 2 }, "z": 1 }
                        """));
    }

    @Test
    void checksumCanonicalizesScientificNotationAndEquivalentDecimalFormats() {
        PublicRuntimeSnapshotCodec codec =
                new PublicRuntimeSnapshotCodec(new ObjectMapper().findAndRegisterModules());

        assertThat(codec.checksum("{\"value\":1e2,\"small\":1.2300e-2}"))
                .isEqualTo(codec.checksum("{\"small\":0.0123,\"value\":100.00}"));
    }

    private RuntimeContentSnapshot actualBundleSnapshot() throws Exception {
        PublicBundleLoader loader = new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(),
                Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json")) {
            files.put(name, readResource("public-data/bundle/" + name));
        }
        return loader.load(files);
    }

    private byte[] readResource(String name) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertThat(stream).as("resource %s", name).isNotNull();
        try (InputStream input = stream) {
            return input.readAllBytes();
        }
    }
}
