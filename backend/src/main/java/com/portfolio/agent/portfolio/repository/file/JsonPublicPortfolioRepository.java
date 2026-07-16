package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;

@Repository
public class JsonPublicPortfolioRepository implements PublicPortfolioRepository {

    private final PortfolioSnapshot snapshot;

    public JsonPublicPortfolioRepository(
            ObjectMapper objectMapper,
            @Value("classpath:public-data/public-portfolio.v1.json") Resource resource,
            PortfolioSnapshotValidator validator
    ) {
        try (InputStream inputStream = resource.getInputStream()) {
            PortfolioSnapshot loaded = objectMapper.readValue(inputStream, PortfolioSnapshot.class);
            validator.validate(loaded);
            this.snapshot = loaded;
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidPortfolioSnapshotException("unable to load public portfolio snapshot", exception);
        }
    }

    @Override
    public PortfolioSnapshot getSnapshot() {
        return snapshot;
    }
}
