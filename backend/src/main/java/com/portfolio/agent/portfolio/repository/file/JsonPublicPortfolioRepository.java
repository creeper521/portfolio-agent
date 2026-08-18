package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.time.Instant;
import java.time.Clock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Repository
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class JsonPublicPortfolioRepository implements PublicPortfolioRepository {

    private final RuntimeContentSnapshot snapshot;

    @Autowired
    public JsonPublicPortfolioRepository(
            ObjectMapper objectMapper,
            @Value("classpath:public-data/bundle/manifest.json") Resource manifest,
            @Value("classpath:public-data/bundle/portfolio.json") Resource portfolio,
            @Value("classpath:public-data/bundle/presentation.json") Resource presentation,
            @Value("classpath:public-data/bundle/rag-documents.jsonl") Resource ragDocuments,
            @Value("classpath:public-data/bundle/keyword-index.json") Resource keywordIndex,
            @Value("classpath:public-data/bundle/vector-index.bin") Resource vectorIndex,
            @Value("classpath:public-data/bundle/checksums.json") Resource checksums,
            @Value("${portfolio.content.release-root:}") String releaseRoot,
            PortfolioSnapshotValidator validator,
            ApplicationStartupDiagnostics startupDiagnostics
    ) {
        long startedAt = System.nanoTime();
        try {
            PublicBundleLoader loader = new PublicBundleLoader(objectMapper, validator, Clock.systemUTC());
            if (releaseRoot != null && !releaseRoot.isBlank()) {
                this.snapshot = new ActiveBundleLocator().load(java.nio.file.Path.of(releaseRoot), loader);
            } else {
                this.snapshot = loader.load(Map.of(
                            "manifest.json", manifest.getContentAsByteArray(),
                            "portfolio.json", portfolio.getContentAsByteArray(),
                            "presentation.json", presentation.getContentAsByteArray(),
                            "rag-documents.jsonl", ragDocuments.getContentAsByteArray(),
                            "keyword-index.json", keywordIndex.getContentAsByteArray(),
                            "vector-index.bin", vectorIndex.getContentAsByteArray(),
                            "checksums.json", checksums.getContentAsByteArray()
                    ));
            }
            publishLoaded(startupDiagnostics, startedAt);
        } catch (IOException exception) {
            startupDiagnostics.contentBundleFailed();
            throw new InvalidPortfolioSnapshotException(
                    "unable to read public release bundle resources", exception);
        } catch (RuntimeException exception) {
            startupDiagnostics.contentBundleFailed();
            throw exception;
        }
    }

    private void publishLoaded(
            ApplicationStartupDiagnostics startupDiagnostics,
            long startedAt
    ) {
        RuntimeRetrievalContent retrieval = snapshot.getRetrievalContent().orElse(null);
        int documentCount = retrieval == null ? 0 : retrieval.getDocuments().size();
        int vectorDimension = retrieval == null
                ? 0
                : retrieval.getVectorIndex().getDimension();
        startupDiagnostics.contentBundleLoaded(
                snapshot.getSchemaVersion(),
                snapshot.getContentVersion(),
                retrieval != null,
                documentCount,
                vectorDimension,
                elapsedMillis(startedAt));
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return "sha256:" + HexFormat.of().formatHex(digest.digest(bytes));
    }

    @Override
    public RuntimeContentSnapshot getSnapshot() {
        return snapshot;
    }
}
