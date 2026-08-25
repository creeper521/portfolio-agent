package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.ingestion.gateway.DocumentEmbeddingPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 本地公开检索装配：按批准的隐私与配置门装配治理导入端口与公开检索端口。
 *
 * <p>BGE 路径仅在这些门全部满足时才真正加载：profile 显式为 HYBRID、
 * 模型目录已配置且通过描述符哈希校验。profile 非 HYBRID 时公开端口装配为
 * 恒抛 LOCAL_EMBEDDING_DISABLED 的 fail-closed 实现；治理导入端口仅在
 * {@code portfolio.database.governance.enabled=true} 时创建，保持私有治理
 * 导入与公开检索两个能力显式分离。
 */
@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfiguration {

    /**
     * 治理导入侧文档 embedding 端口：仅当治理导入能力显式开启时装配，
     * 模型在首次调用时懒加载并校验。
     */
    @Bean(name = "governanceDocumentEmbeddingPort")
    @ConditionalOnProperty(prefix = "portfolio.database.governance", name = "enabled", havingValue = "true")
    DocumentEmbeddingPort governanceDocumentEmbeddingPort(RetrievalProperties properties) {
        return new GovernanceDocumentEmbeddingPort(properties, new LocalEmbeddingArtifactVerifier());
    }

    /**
     * 公开运行时的本地 embedding 端口：HYBRID profile 下在启动期校验工件、
     * 初始化 ONNX 适配器并发布启动诊断（维度与加载耗时）；初始化失败记入
     * 启动诊断后原样上抛，使应用按 fail-closed 启动失败。
     * 非 HYBRID profile 返回恒失败的占位实现。
     */
    @Bean
    LocalEmbeddingPort localEmbeddingPort(
            RetrievalProperties properties,
            ApplicationStartupDiagnostics startupDiagnostics
    ) {
        if (properties.getProfile() != RetrievalProfile.HYBRID) {
            return localText -> {
                throw new LocalEmbeddingFailureException("LOCAL_EMBEDDING_DISABLED");
            };
        }
        long startedAt = System.nanoTime();
        try {
            String configuredDirectory = properties.getModelDirectory() == null
                    ? ""
                    : properties.getModelDirectory().strip();
            if (configuredDirectory.isEmpty()) {
                throw new LocalEmbeddingFailureException("LOCAL_MODEL_DIRECTORY_REQUIRED");
            }
            Path modelDirectory = Path.of(configuredDirectory);
            LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                    .verify(modelDirectory);
            LocalEmbeddingPort embeddingPort = new OnnxLocalEmbeddingAdapter(
                    modelDirectory,
                    artifact.getQueryInstruction(),
                    artifact.getMaxTokens(),
                    artifact.getDimension(),
                    artifact.getIntraOpThreads(),
                    artifact.getInterOpThreads());
            startupDiagnostics.embeddingModelLoaded(
                    artifact.getDimension(),
                    Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
            return embeddingPort;
        } catch (RuntimeException exception) {
            startupDiagnostics.embeddingModelFailed();
            throw exception;
        }
    }

}
