package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.Instant;
import java.time.Clock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Repository
public class JsonPublicPortfolioRepository implements PublicPortfolioRepository {

    private final RuntimeContentSnapshot snapshot;

    @Autowired
    public JsonPublicPortfolioRepository(
            ObjectMapper objectMapper,
            @Value("classpath:public-data/bundle/manifest.json") Resource manifest,
            @Value("classpath:public-data/bundle/portfolio.json") Resource portfolio,
            @Value("classpath:public-data/bundle/presentation.json") Resource presentation,
            @Value("classpath:public-data/bundle/checksums.json") Resource checksums,
            @Value("${portfolio.content.release-root:}") String releaseRoot,
            PortfolioSnapshotValidator validator
    ) {
        try {
            PublicBundleLoader loader = new PublicBundleLoader(objectMapper, validator, Clock.systemUTC());
            if (releaseRoot != null && !releaseRoot.isBlank()) {
                this.snapshot = new ActiveBundleLocator().load(java.nio.file.Path.of(releaseRoot), loader);
            } else {
                this.snapshot = loader.load(Map.of(
                            "manifest.json", manifest.getContentAsByteArray(),
                            "portfolio.json", portfolio.getContentAsByteArray(),
                            "presentation.json", presentation.getContentAsByteArray(),
                            "checksums.json", checksums.getContentAsByteArray()
                    ));
            }
        } catch (IOException exception) {
            throw new InvalidPortfolioSnapshotException(
                    "unable to read public release bundle resources", exception);
        }
    }

    public JsonPublicPortfolioRepository(
            ObjectMapper objectMapper,
            Resource resource,
            PortfolioSnapshotValidator validator
    ) {
        try {
            byte[] bytes = resource.getContentAsByteArray();
            PortfolioSnapshot loaded = objectMapper.readValue(bytes, PortfolioSnapshot.class);
            validator.validate(loaded);
            this.snapshot = new RuntimeContentSnapshot(loaded, sha256(bytes), Instant.now());
        } catch (IOException | IllegalArgumentException | NoSuchAlgorithmException exception) {
            throw new InvalidPortfolioSnapshotException("unable to load public portfolio snapshot", exception);
        }
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
