package com.portfolio.agent.portfolio.repository.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BundleHashCalculatorTest {

    @Test
    void includesCanonicalRagDocumentsWhenPresent() {
        Map<String, byte[]> first = payload("first chunk");
        Map<String, byte[]> second = payload("second chunk");

        assertThat(BundleHashCalculator.candidatePayloadHash(first))
                .isNotEqualTo(BundleHashCalculator.candidatePayloadHash(second));
    }

    @Test
    void derivedIndexesDoNotChangeCandidatePayloadHash() {
        Map<String, byte[]> first = payload("same chunk");
        Map<String, byte[]> second = payload("same chunk");
        first.put("keyword-index.json", bytes("first index"));
        second.put("keyword-index.json", bytes("second index"));
        first.put("vector-index.bin", bytes("first vector"));
        second.put("vector-index.bin", bytes("second vector"));

        assertThat(BundleHashCalculator.candidatePayloadHash(first))
                .isEqualTo(BundleHashCalculator.candidatePayloadHash(second));
    }

    @Test
    void retainsLegacyTwoFileCandidateHashWhenRagDocumentsAreAbsent() {
        Map<String, byte[]> payload = new LinkedHashMap<>();
        payload.put("portfolio.json", bytes("portfolio"));
        payload.put("presentation.json", bytes("presentation"));

        assertThat(BundleHashCalculator.candidatePayloadHash(payload))
                .isEqualTo("sha256:1464ffb26a8ff43153fd8e8f156f28147069afc135c09c7a3a83d1fe2cccf2d7");
    }

    private Map<String, byte[]> payload(String ragDocuments) {
        Map<String, byte[]> payload = new LinkedHashMap<>();
        payload.put("portfolio.json", bytes("portfolio"));
        payload.put("presentation.json", bytes("presentation"));
        payload.put("rag-documents.jsonl", bytes(ragDocuments));
        return payload;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
