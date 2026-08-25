package com.portfolio.agent.infrastructure.retrieval.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地公开检索配置：检索 profile 与本地 BGE 模型目录。
 *
 * <p>profile 缺省 DISABLED（fail-closed）；只有显式 HYBRID 且 modelDirectory
 * 非空并通过工件校验，本地 embedding 才会启用。
 */
@ConfigurationProperties(prefix = "portfolio.retrieval")
public final class RetrievalProperties {

    /** 检索 profile，缺省 DISABLED。 */
    private RetrievalProfile profile = RetrievalProfile.DISABLED;
    /** 本地 BGE 模型目录（HYBRID 下必填，需通过描述符哈希校验）。 */
    private String modelDirectory = "";

    public RetrievalProfile getProfile() {
        return profile;
    }

    public void setProfile(RetrievalProfile profile) {
        this.profile = profile;
    }

    public String getModelDirectory() {
        return modelDirectory;
    }

    public void setModelDirectory(String modelDirectory) {
        this.modelDirectory = modelDirectory;
    }
}
