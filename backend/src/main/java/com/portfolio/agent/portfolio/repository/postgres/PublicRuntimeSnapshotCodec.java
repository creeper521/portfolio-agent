package com.portfolio.agent.portfolio.repository.postgres;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeKeywordIndex;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeVectorIndex;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class PublicRuntimeSnapshotCodec {

    private final ObjectMapper mapper;

    public PublicRuntimeSnapshotCodec(ObjectMapper objectMapper) {
        this.mapper = Objects.requireNonNull(objectMapper, "objectMapper").copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public EncodedRuntimeSnapshot encode(RuntimeContentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        RuntimeRetrievalContent retrieval = snapshot.getRetrievalContent()
                .orElseThrow(() -> new IllegalArgumentException("retrieval content is required"));
        PortfolioSnapshot content = new PortfolioSnapshot(
                snapshot.getSchemaVersion(), snapshot.getContentVersion(), snapshot.getPublishedAt(),
                snapshot.getOwner(), snapshot.getProjects(), snapshot.getCases(), snapshot.getCollections(),
                snapshot.getClaims(), snapshot.getClaimEvidenceLinks(), snapshot.getQuestions(),
                snapshot.getApprovedEvidence(), snapshot.getTimeline());
        SnapshotPayload payload = new SnapshotPayload(
                snapshot.getRuntimeBundleHash(), snapshot.getLoadedAt(), content,
                RetrievalPayload.from(retrieval));
        try {
            String json = mapper.writeValueAsString(payload);
            return new EncodedRuntimeSnapshot(json, checksum(json));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("unable to encode public runtime snapshot", exception);
        }
    }

    public RuntimeContentSnapshot decode(String payload) {
        try {
            SnapshotPayload decoded = mapper.readValue(payload, SnapshotPayload.class);
            return new RuntimeContentSnapshot(
                    decoded.getContent(),
                    decoded.getRuntimeBundleHash(),
                    decoded.getLoadedAt(),
                    decoded.getRetrieval().toDomain());
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IllegalArgumentException("unable to decode public runtime snapshot", exception);
        }
    }

    public String checksum(String payload) {
        Objects.requireNonNull(payload, "payload");
        try {
            JsonNode parsed = mapper.readTree(payload);
            return sha256(mapper.writeValueAsString(canonicalize(parsed)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("unable to canonicalize public runtime snapshot", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode canonical = mapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                canonical.set(name, canonicalize(node.get(name)));
            }
            return canonical;
        }
        if (node.isArray()) {
            ArrayNode canonical = mapper.createArrayNode();
            for (JsonNode item : node) {
                canonical.add(canonicalize(item));
            }
            return canonical;
        }
        return node;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class SnapshotPayload {
        private final String runtimeBundleHash;
        private final Instant loadedAt;
        private final PortfolioSnapshot content;
        private final RetrievalPayload retrieval;

        @JsonCreator
        private SnapshotPayload(
                @JsonProperty("runtimeBundleHash") String runtimeBundleHash,
                @JsonProperty("loadedAt") Instant loadedAt,
                @JsonProperty("content") PortfolioSnapshot content,
                @JsonProperty("retrieval") RetrievalPayload retrieval) {
            this.runtimeBundleHash = Objects.requireNonNull(runtimeBundleHash, "runtimeBundleHash");
            this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
            this.content = Objects.requireNonNull(content, "content");
            this.retrieval = Objects.requireNonNull(retrieval, "retrieval");
        }

        public String getRuntimeBundleHash() {
            return runtimeBundleHash;
        }

        public Instant getLoadedAt() {
            return loadedAt;
        }

        public PortfolioSnapshot getContent() {
            return content;
        }

        public RetrievalPayload getRetrieval() {
            return retrieval;
        }
    }

    private static final class RetrievalPayload {
        private final RetrievalManifest manifest;
        private final List<RagDocument> documents;
        private final KeywordIndexPayload keywordIndex;
        private final VectorIndexPayload vectorIndex;

        @JsonCreator
        private RetrievalPayload(
                @JsonProperty("manifest") RetrievalManifest manifest,
                @JsonProperty("documents") List<RagDocument> documents,
                @JsonProperty("keywordIndex") KeywordIndexPayload keywordIndex,
                @JsonProperty("vectorIndex") VectorIndexPayload vectorIndex) {
            this.manifest = Objects.requireNonNull(manifest, "manifest");
            this.documents = List.copyOf(documents);
            this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex");
            this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
        }

        private static RetrievalPayload from(RuntimeRetrievalContent content) {
            return new RetrievalPayload(
                    content.getManifest(),
                    content.getDocuments(),
                    KeywordIndexPayload.from(content.getKeywordIndex()),
                    VectorIndexPayload.from(content.getVectorIndex()));
        }

        private RuntimeRetrievalContent toDomain() {
            return new RuntimeRetrievalContent(
                    manifest, documents, keywordIndex.toDomain(), vectorIndex.toDomain());
        }

        public RetrievalManifest getManifest() {
            return manifest;
        }

        public List<RagDocument> getDocuments() {
            return documents;
        }

        public KeywordIndexPayload getKeywordIndex() {
            return keywordIndex;
        }

        public VectorIndexPayload getVectorIndex() {
            return vectorIndex;
        }
    }

    private static final class KeywordIndexPayload {
        private final int documentCount;
        private final double averageDocumentLength;
        private final List<KeywordDocumentPayload> documents;
        private final Map<String, Integer> documentFrequencies;

        @JsonCreator
        private KeywordIndexPayload(
                @JsonProperty("documentCount") int documentCount,
                @JsonProperty("averageDocumentLength") double averageDocumentLength,
                @JsonProperty("documents") List<KeywordDocumentPayload> documents,
                @JsonProperty("documentFrequencies") Map<String, Integer> documentFrequencies) {
            this.documentCount = documentCount;
            this.averageDocumentLength = averageDocumentLength;
            this.documents = List.copyOf(documents);
            this.documentFrequencies = Map.copyOf(documentFrequencies);
        }

        private static KeywordIndexPayload from(RuntimeKeywordIndex index) {
            List<KeywordDocumentPayload> entries = index.getDocuments().stream()
                    .map(KeywordDocumentPayload::from)
                    .toList();
            return new KeywordIndexPayload(
                    index.getDocumentCount(), index.getAverageDocumentLength(),
                    entries, index.getDocumentFrequencies());
        }

        private RuntimeKeywordIndex toDomain() {
            List<RuntimeKeywordIndex.DocumentEntry> entries = documents.stream()
                    .map(KeywordDocumentPayload::toDomain)
                    .toList();
            return new RuntimeKeywordIndex(
                    documentCount, averageDocumentLength, entries, documentFrequencies);
        }

        public int getDocumentCount() {
            return documentCount;
        }

        public double getAverageDocumentLength() {
            return averageDocumentLength;
        }

        public List<KeywordDocumentPayload> getDocuments() {
            return documents;
        }

        public Map<String, Integer> getDocumentFrequencies() {
            return documentFrequencies;
        }
    }

    private static final class KeywordDocumentPayload {
        private final String chunkId;
        private final int documentLength;
        private final Map<String, Integer> termFrequencies;

        @JsonCreator
        private KeywordDocumentPayload(
                @JsonProperty("chunkId") String chunkId,
                @JsonProperty("documentLength") int documentLength,
                @JsonProperty("termFrequencies") Map<String, Integer> termFrequencies) {
            this.chunkId = Objects.requireNonNull(chunkId, "chunkId");
            this.documentLength = documentLength;
            this.termFrequencies = Map.copyOf(termFrequencies);
        }

        private static KeywordDocumentPayload from(RuntimeKeywordIndex.DocumentEntry entry) {
            return new KeywordDocumentPayload(
                    entry.getChunkId(), entry.getDocumentLength(), entry.getTermFrequencies());
        }

        private RuntimeKeywordIndex.DocumentEntry toDomain() {
            return new RuntimeKeywordIndex.DocumentEntry(chunkId, documentLength, termFrequencies);
        }

        public String getChunkId() {
            return chunkId;
        }

        public int getDocumentLength() {
            return documentLength;
        }

        public Map<String, Integer> getTermFrequencies() {
            return termFrequencies;
        }
    }

    private static final class VectorIndexPayload {
        private final int dimension;
        private final Map<String, float[]> vectors;

        @JsonCreator
        private VectorIndexPayload(
                @JsonProperty("dimension") int dimension,
                @JsonProperty("vectors") Map<String, float[]> vectors) {
            this.dimension = dimension;
            this.vectors = copyVectors(vectors);
        }

        private static VectorIndexPayload from(RuntimeVectorIndex index) {
            return new VectorIndexPayload(index.getDimension(), index.getVectors());
        }

        private RuntimeVectorIndex toDomain() {
            return new RuntimeVectorIndex(dimension, vectors);
        }

        public int getDimension() {
            return dimension;
        }

        public Map<String, float[]> getVectors() {
            return copyVectors(vectors);
        }

        private static Map<String, float[]> copyVectors(Map<String, float[]> source) {
            Map<String, float[]> copied = new LinkedHashMap<>();
            for (Map.Entry<String, float[]> entry : source.entrySet()) {
                copied.put(entry.getKey(), entry.getValue().clone());
            }
            return copied;
        }
    }
}
