package com.portfolio.agent.ingestion.adapter.postgres;

import com.portfolio.agent.ingestion.domain.ImportedMarkdownChunk;
import com.portfolio.agent.ingestion.domain.ImportedMarkdownDocument;
import com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * {@link MarkdownGovernanceStore} 的 PostgreSQL 实现：直接读写治理库中的
 * source_document / source_revision / source_chunk 三张表。
 *
 * <p>该仓储只服务私有治理导入这一显式运维能力，操作的是治理库投影表，
 * 不触碰私有 Obsidian 知识库文件本身。所有"最新版本"语义均通过
 * LATERAL 子查询按 imported_at、revision_id 降序取第一条实现；
 * 向量以 pgvector 文本字面量（"[v1,v2,...]"）在 Java 侧与 float[] 之间转换。
 * 写入方法均为数据库写副作用操作，需由调用方（导入服务）在事务内调用。
 */
public final class PostgresSourceDocumentRepository implements MarkdownGovernanceStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresSourceDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * 返回所有 ACTIVE 文档的相对路径到其最新 revision 内容哈希的映射。
     * 用于导入前的增量比对：文件哈希未变化的文档可跳过重新导入。
     */
    @Override
    public Map<String, String> knownDocuments() {
        Map<String, String> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT document.relative_path, revision.content_hash
                FROM source_document document
                JOIN LATERAL (
                    SELECT candidate.content_hash
                    FROM source_revision candidate
                    WHERE candidate.document_id = document.document_id
                    ORDER BY candidate.imported_at DESC, candidate.revision_id DESC
                    LIMIT 1
                ) revision ON true
                WHERE document.lifecycle_status = 'ACTIVE'
                """, (RowCallbackHandler) resultSet -> result.put(resultSet.getString(1), resultSet.getString(2)));
        return Map.copyOf(result);
    }

    /**
     * 查询单个 ACTIVE 文档最新 revision 的内容哈希。
     *
     * @param relativePath 文档相对路径
     * @return 最新 revision 的 content_hash；文档不存在或已非 ACTIVE 时为 {@link Optional#empty()}
     */
    @Override
    public Optional<String> contentHash(String relativePath) {
        List<String> values = jdbcTemplate.query("""
                SELECT revision.content_hash
                FROM source_document document
                JOIN LATERAL (
                    SELECT candidate.content_hash
                    FROM source_revision candidate
                    WHERE candidate.document_id = document.document_id
                    ORDER BY candidate.imported_at DESC, candidate.revision_id DESC
                    LIMIT 1
                ) revision ON true
                WHERE document.relative_path = ? AND document.lifecycle_status = 'ACTIVE'
                """, (resultSet, row) -> resultSet.getString(1), relativePath);
        return values.stream().findFirst();
    }

    /**
     * 返回最新 revision 的 parse_status 仍为 VECTOR_PENDING 的 ACTIVE 文档相对路径集合，
     * 即"已入库但向量化尚未完成"的待补处理清单。
     */
    @Override
    public Set<String> pendingDocuments() {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT document.relative_path
                FROM source_document document
                JOIN LATERAL (
                    SELECT candidate.parse_status
                    FROM source_revision candidate
                    WHERE candidate.document_id = document.document_id
                    ORDER BY candidate.imported_at DESC, candidate.revision_id DESC
                    LIMIT 1
                ) revision ON true
                WHERE document.lifecycle_status = 'ACTIVE'
                  AND revision.parse_status = 'VECTOR_PENDING'
                """, String.class));
    }

    /**
     * 查找同文档下可复用的历史 chunk 向量：仅当同一相对路径、同一 chunk 哈希的历史 chunk
     * 已有 vector_status = 'READY' 且 embedding 非空时，才返回该向量。
     * 用于增量导入时跳过重复调用向量化端口。
     *
     * @param relativePath 文档相对路径
     * @param chunkHashes  当前解析出的 chunk 内容哈希集合
     * @return 哈希到向量的映射；无可复用项时为空映射，不含未就绪的条目
     */
    @Override
    public Map<String, float[]> reusableEmbeddings(String relativePath, Set<String> chunkHashes) {
        Map<String, float[]> result = new HashMap<>();
        for (String hash : chunkHashes) {
            List<String> values = jdbcTemplate.query("""
                    SELECT chunk.embedding::text
                    FROM source_chunk chunk
                    JOIN source_revision revision ON revision.revision_id = chunk.revision_id
                    JOIN source_document document ON document.document_id = revision.document_id
                    WHERE document.relative_path = ?
                      AND chunk.chunk_hash = ?
                      AND chunk.vector_status = 'READY'
                      AND chunk.embedding IS NOT NULL
                    ORDER BY revision.imported_at DESC
                    LIMIT 1
                    """, (resultSet, row) -> resultSet.getString(1), relativePath, hash);
            if (!values.isEmpty()) {
                result.put(hash, parseVector(values.getFirst()));
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 保存一个 Markdown 文档 revision：按相对路径定位（或新建）source_document，
     * 按"文档 + 内容哈希"定位（或新建）source_revision，然后整批重建 source_chunk。
     *
     * <p>写入规则：
     * <ul>
     *   <li>文档已存在则置回 ACTIVE 并刷新 last_seen_at；不存在则插入新 ACTIVE 文档。</li>
     *   <li>同一文档下已存在相同内容哈希的 revision 视为重导入：先删除其全部 chunk
     *       再重写，并清空 error_code、刷新 parse_status 与 imported_at。</li>
     *   <li>chunk 的 embedding 以 pgvector 字面量写入，未向量化的 chunk 存 NULL。</li>
     *   <li>仅当文档声明 {@code replaceCurrentRevision} 时才把该 revision 提升为
     *       current_revision_id（例如解析失败需要记录现场时不提升）。</li>
     * </ul>
     * 数据库写副作用：本方法执行多条 INSERT/UPDATE/DELETE，必须由调用方包在事务中。
     *
     * @param document 已完成解析（可能含向量复用结果）的导入文档
     */
    @Override
    public void saveRevision(ImportedMarkdownDocument document) {
        String documentId = findDocumentId(document.getRelativePath());
        if (documentId == null) {
            documentId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO source_document (document_id, relative_path, lifecycle_status)
                    VALUES (CAST(? AS uuid), ?, 'ACTIVE')
                    """, documentId, document.getRelativePath());
        } else {
            jdbcTemplate.update("""
                    UPDATE source_document
                    SET lifecycle_status = 'ACTIVE', last_seen_at = now()
                    WHERE document_id = CAST(? AS uuid)
                    """, documentId);
        }
        String parseStatus = document.getRevisionStatus().name();
        String revisionId = findRevisionId(documentId, document.getContentHash());
        if (revisionId == null) {
            revisionId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO source_revision (revision_id, document_id, content_hash, byte_size, parse_status)
                    VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?)
                    """, revisionId, documentId, document.getContentHash(), document.getByteSize(), parseStatus);
        } else {
            // 同内容 revision 重导入：chunk 集合可能因解析规则变化而不同，先整批清空再重建
            jdbcTemplate.update("DELETE FROM source_chunk WHERE revision_id = CAST(? AS uuid)", revisionId);
            jdbcTemplate.update("""
                    UPDATE source_revision
                    SET byte_size = ?, parse_status = ?, error_code = NULL, imported_at = now()
                    WHERE revision_id = CAST(? AS uuid)
                    """, document.getByteSize(), parseStatus, revisionId);
        }
        for (ImportedMarkdownChunk chunk : document.getChunks()) {
            jdbcTemplate.update("""
                    INSERT INTO source_chunk
                        (chunk_id, revision_id, ordinal, chunk_hash, private_text, embedding, vector_status)
                    VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, CAST(? AS vector), ?)
                    """, UUID.randomUUID().toString(), revisionId, chunk.getOrdinal(), chunk.getHash(),
                    chunk.getPrivateText(), chunk.getEmbedding() == null ? null : vectorLiteral(chunk.getEmbedding()),
                    chunk.getVectorStatus().name());
        }
        if (document.isReplaceCurrentRevision()) {
            // 只有被声明为"可作为当前版本"的 revision 才提升 current_revision_id，失败现场不覆盖当前版本
            jdbcTemplate.update("""
                    UPDATE source_document
                    SET current_revision_id = CAST(? AS uuid), lifecycle_status = 'ACTIVE', last_seen_at = now()
                    WHERE document_id = CAST(? AS uuid)
                    """, revisionId, documentId);
        }
    }

    /**
     * 将指定文档标记为 MISSING：源目录中已不存在的文档保留历史记录但退出 ACTIVE 状态，
     * 后续导入比对与待补清单都会排除它。重复标记是幂等的（仅更新非 MISSING 行）。
     *
     * @param relativePath 文档相对路径
     */
    @Override
    public void markMissing(String relativePath) {
        jdbcTemplate.update("""
                UPDATE source_document
                SET lifecycle_status = 'MISSING', last_seen_at = now()
                WHERE relative_path = ? AND lifecycle_status <> 'MISSING'
                """, relativePath);
    }

    /** 按相对路径查找文档 ID，不存在时返回 null。 */
    private String findDocumentId(String relativePath) {
        List<String> values = jdbcTemplate.query(
                "SELECT document_id::text FROM source_document WHERE relative_path = ?",
                (resultSet, row) -> resultSet.getString(1), relativePath);
        return values.isEmpty() ? null : values.getFirst();
    }

    /** 按文档 ID 与内容哈希查找 revision ID，用于识别"同一内容再次导入"的场景。 */
    private String findRevisionId(String documentId, String contentHash) {
        List<String> values = jdbcTemplate.query("""
                SELECT revision_id::text FROM source_revision
                WHERE document_id = CAST(? AS uuid) AND content_hash = ?
                """, (resultSet, row) -> resultSet.getString(1), documentId, contentHash);
        return values.isEmpty() ? null : values.getFirst();
    }

    /**
     * 把 pgvector 的文本表示（如 "[0.1,0.2]"）解析回 float 数组。
     * 空向量字面量 "[]" 解析为长度 0 的数组。
     */
    private float[] parseVector(String literal) {
        String values = literal.substring(1, literal.length() - 1);
        if (values.isBlank()) { return new float[0]; }
        String[] parts = values.split(",");
        float[] vector = new float[parts.length];
        for (int index = 0; index < parts.length; index++) {
            vector[index] = Float.parseFloat(parts[index]);
        }
        return vector;
    }

    /** 把 float 数组序列化为 pgvector 接受的 "[v1,v2,...]" 字面量，配合 CAST(? AS vector) 写入。 */
    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) { builder.append(','); }
            builder.append(Float.toString(vector[index]));
        }
        return builder.append(']').toString();
    }
}
