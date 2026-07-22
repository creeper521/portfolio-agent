package com.portfolio.agent.portfolio.release;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.common.text.RetrievalTextNormalizer;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import com.portfolio.agent.portfolio.repository.file.KeywordIndexFile;
import com.portfolio.agent.portfolio.repository.file.VectorIndexCodec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class RetrievalBundleCompiler {

    public static final String STRATEGY_VERSION = "claim-chunk-v1";
    public static final String RETRIEVAL_POLICY_VERSION = "retrieval-policy-v1";
    public static final String EMBEDDING_MODEL_ID = "BAAI/bge-small-zh-v1.5";
    public static final String VECTOR_INDEX_FORMAT_VERSION = "vector-index-v1";

    private final ObjectMapper objectMapper;
    private final DocumentEmbeddingPort embeddingPort;
    private final String embeddingArtifactSha256;
    private final int dimension;

    public RetrievalBundleCompiler(
            DocumentEmbeddingPort embeddingPort,
            String embeddingArtifactSha256,
            int dimension
    ) {
        if (embeddingPort == null || embeddingArtifactSha256 == null
                || embeddingArtifactSha256.isBlank() || dimension <= 0) {
            throw new IllegalArgumentException("retrieval compiler configuration is invalid");
        }
        this.embeddingPort = embeddingPort;
        this.embeddingArtifactSha256 = embeddingArtifactSha256;
        this.dimension = dimension;
        this.objectMapper = canonicalMapper();
    }

    public RetrievalCompilation compile(PortfolioSnapshot snapshot, LocalDate currentDate) {
        try {
            List<RagDocument> documents = validatedDocuments(snapshot, currentDate);
            byte[] ragBytes = writeJsonLines(objectMapper, documents);
            KeywordIndexFile keywordIndex = new KeywordIndexBuilder().build(documents);
            byte[] keywordBytes = objectMapper.writeValueAsBytes(keywordIndex);
            Map<String, float[]> vectors = new LocalDocumentEmbeddingBuilder(
                    embeddingPort, dimension).build(documents);
            byte[] vectorBytes = new VectorIndexCodec().encode(vectors, dimension);
            RetrievalManifest manifest = new RetrievalManifest(
                    STRATEGY_VERSION,
                    RetrievalTextNormalizer.VERSION,
                    RETRIEVAL_POLICY_VERSION,
                    EMBEDDING_MODEL_ID,
                    embeddingArtifactSha256,
                    dimension,
                    256,
                    "L2",
                    "COSINE",
                    documents.size(),
                    BundleHashCalculator.sha256(ragBytes),
                    KeywordIndexBuilder.FORMAT_VERSION,
                    VECTOR_INDEX_FORMAT_VERSION);
            return new RetrievalCompilation(ragBytes, keywordBytes, vectorBytes, manifest);
        } catch (InvalidPortfolioSnapshotException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidPortfolioSnapshotException(
                    "unable to compile retrieval bundle", exception);
        }
    }

    public static byte[] compileCanonicalDocuments(
            PortfolioSnapshot snapshot,
            LocalDate validFrom
    ) {
        try {
            return writeJsonLines(canonicalMapper(), validatedDocuments(snapshot, validFrom));
        } catch (InvalidPortfolioSnapshotException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidPortfolioSnapshotException(
                    "unable to compile canonical RAG documents", exception);
        }
    }

    private static List<RagDocument> validatedDocuments(
            PortfolioSnapshot snapshot,
            LocalDate validFrom
    ) {
        List<RagDocument> documents = new ClaimRagDocumentBuilder()
                .build(snapshot, validFrom);
        new RagDocumentValidator().validate(snapshot, documents, validFrom);
        return documents;
    }

    private static ObjectMapper canonicalMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    private static byte[] writeJsonLines(
            ObjectMapper mapper,
            List<RagDocument> documents
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (RagDocument document : documents) {
            output.writeBytes(mapper.writeValueAsBytes(document));
            output.write('\n');
        }
        return output.toByteArray();
    }
}
