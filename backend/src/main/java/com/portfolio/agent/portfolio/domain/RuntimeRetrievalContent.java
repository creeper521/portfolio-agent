package com.portfolio.agent.portfolio.domain;

import java.util.List;
import java.util.Objects;

/**
 * 运行时检索内容：随公开快照加载的本地检索三件套。
 *
 * <p>由 {@link RetrievalManifest}（口径指纹）、{@link RagDocument} 列表（公开文本切块）、
 * {@link RuntimeKeywordIndex}（关键词检索统计量）与 {@link RuntimeVectorIndex}
 * （切块向量）组成，供本地公开检索在无需外部服务的情况下召回相关内容。
 * manifest 与两个索引不允许为 null；文档列表为空表示该发布包未附检索内容。
 */
public final class RuntimeRetrievalContent {

    private final RetrievalManifest manifest;
    private final List<RagDocument> documents;
    private final RuntimeKeywordIndex keywordIndex;
    private final RuntimeVectorIndex vectorIndex;

    public RuntimeRetrievalContent(
            RetrievalManifest manifest,
            List<RagDocument> documents,
            RuntimeKeywordIndex keywordIndex,
            RuntimeVectorIndex vectorIndex
    ) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.documents = List.copyOf(documents);
        this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex");
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
    }

    public RetrievalManifest getManifest() { return manifest; }
    public List<RagDocument> getDocuments() { return documents; }
    public RuntimeKeywordIndex getKeywordIndex() { return keywordIndex; }
    public RuntimeVectorIndex getVectorIndex() { return vectorIndex; }
}
