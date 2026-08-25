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

/**
 * 检索发布包编译器：把作品集快照编译为可直接发布的检索产物集合。
 *
 * <p>编译流程：声明 → RAG 文档（构建 + 校验）→ 规范化 JSONL 字节、关键词索引字节、
 * 本地向量索引字节，并生成记录策略/嵌入模型/维度与各产物哈希的
 * {@link RetrievalManifest}。JSON 序列化使用属性按字母排序、Map 键有序的规范化配置，
 * 保证同一输入的编译字节可复现。
 *
 * <p>失败行为：配置非法时构造器抛出 {@link IllegalArgumentException}；
 * 编译过程除快照校验异常外，其余 IOException/RuntimeException 一律包装为
 * {@link InvalidPortfolioSnapshotException}。
 */
public final class RetrievalBundleCompiler {

    /** 声明分块策略版本：声明级 chunk。 */
    public static final String STRATEGY_VERSION = "claim-chunk-v1";
    /** 检索策略版本，随 query 风险策略演进。 */
    public static final String RETRIEVAL_POLICY_VERSION = "retrieval-policy-v2.1-query-risk";
    /** 本地嵌入模型标识（BGE 中文小模型）。 */
    public static final String EMBEDDING_MODEL_ID = "BAAI/bge-small-zh-v1.5";
    /** 向量索引文件格式版本。 */
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

    /**
     * 编译快照为完整检索发布包。
     *
     * <p>依次产出 RAG 文档 JSONL、关键词索引、向量索引与清单（含 RAG 字节哈希）。
     * 文档在生成后立即校验，校验失败即终止编译。
     *
     * @param snapshot    作品集快照
     * @param currentDate 校验基准日期，同时作为文档 validFrom
     * @return 四个产物（字节 + manifest）的编译结果
     * @throws InvalidPortfolioSnapshotException 文档构建/校验失败或产物编码失败
     */
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

    /**
     * 仅编译规范化 RAG 文档 JSONL 字节（不含索引与向量），供内容哈希比对等场景使用。
     *
     * @param snapshot  作品集快照
     * @param validFrom 文档生效日，同时作为校验基准日期
     * @return 规范化 JSONL 字节
     * @throws InvalidPortfolioSnapshotException 构建/校验失败或序列化失败
     */
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

    /**
     * 构建并校验声明 RAG 文档，供编译与规范化输出两条路径复用。
     */
    private static List<RagDocument> validatedDocuments(
            PortfolioSnapshot snapshot,
            LocalDate validFrom
    ) {
        List<RagDocument> documents = new ClaimRagDocumentBuilder()
                .build(snapshot, validFrom);
        new RagDocumentValidator().validate(snapshot, documents, validFrom);
        return documents;
    }

    /**
     * 创建规范化 ObjectMapper：Java 时间模块 + 属性按字母排序 + Map 键有序，
     * 确保序列化字节与字段声明顺序无关。
     */
    private static ObjectMapper canonicalMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    /**
     * 把文档列表序列化为每行一个 JSON 对象的 JSONL 字节，顺序与输入一致。
     */
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
