package com.portfolio.agent.common.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalLogEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String DIRECTORY_PROPERTY = "portfolio.log-directory";
    static final String FILE_LOGGING_PROFILE = "local-file-logging";
    private static final AtomicBoolean UNRESOLVED_REPORTED = new AtomicBoolean();

    private final LocalLogDirectoryResolver resolver = new LocalLogDirectoryResolver();

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            return;
        }
        String explicit = environment.getProperty("PORTFOLIO_LOG_DIRECTORY");
        Optional<Path> directory = resolver.resolve(
                Path.of(System.getProperty("user.dir", ".")), explicit);
        if (directory.isEmpty()) {
            if (UNRESOLVED_REPORTED.compareAndSet(false, true)) {
                System.err.println(
                        "LOG_LAYOUT_UNRESOLVED reason=REPOSITORY_MARKERS_NOT_FOUND");
            }
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "portfolioLocalFileLogging",
                Map.of(DIRECTORY_PROPERTY, directory.orElseThrow().toString())));
        environment.addActiveProfile(FILE_LOGGING_PROFILE);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
