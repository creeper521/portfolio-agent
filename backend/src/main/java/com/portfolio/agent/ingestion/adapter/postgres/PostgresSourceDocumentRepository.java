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

public final class PostgresSourceDocumentRepository implements MarkdownGovernanceStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresSourceDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

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
            jdbcTemplate.update("""
                    UPDATE source_document
                    SET current_revision_id = CAST(? AS uuid), lifecycle_status = 'ACTIVE', last_seen_at = now()
                    WHERE document_id = CAST(? AS uuid)
                    """, revisionId, documentId);
        }
    }

    @Override
    public void markMissing(String relativePath) {
        jdbcTemplate.update("""
                UPDATE source_document
                SET lifecycle_status = 'MISSING', last_seen_at = now()
                WHERE relative_path = ? AND lifecycle_status <> 'MISSING'
                """, relativePath);
    }

    private String findDocumentId(String relativePath) {
        List<String> values = jdbcTemplate.query(
                "SELECT document_id::text FROM source_document WHERE relative_path = ?",
                (resultSet, row) -> resultSet.getString(1), relativePath);
        return values.isEmpty() ? null : values.getFirst();
    }

    private String findRevisionId(String documentId, String contentHash) {
        List<String> values = jdbcTemplate.query("""
                SELECT revision_id::text FROM source_revision
                WHERE document_id = CAST(? AS uuid) AND content_hash = ?
                """, (resultSet, row) -> resultSet.getString(1), documentId, contentHash);
        return values.isEmpty() ? null : values.getFirst();
    }

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

    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) { builder.append(','); }
            builder.append(Float.toString(vector[index]));
        }
        return builder.append(']').toString();
    }
}
