package com.portfolio.agent.database;

import com.portfolio.agent.PortfolioAgentApplication;
import java.io.OutputStream;
import java.io.PrintStream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class DatabaseMigrationCli {

    private static final Object FRAMEWORK_IO_MONITOR = new Object();

    private DatabaseMigrationCli() {
    }

    public static void main(String[] args) {
        int exitCode = execute(System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(PrintStream output, PrintStream error) {
        ConfigurableApplicationContext context = null;
        try {
            context = runWithFrameworkOutputSuppressed(() ->
                    new SpringApplicationBuilder(PortfolioAgentApplication.class)
                            .web(WebApplicationType.NONE)
                            .bannerMode(Banner.Mode.OFF)
                            .logStartupInfo(false)
                            .registerShutdownHook(false)
                            .run(applicationArguments()));
            Flyway publicFlyway = context.getBean("publicPortfolioFlyway", Flyway.class);
            Flyway governanceFlyway = context.getBean("governanceFlyway", Flyway.class);
            output.println("{\"status\":\"MIGRATED\",\"publicVersion\":\""
                    + currentVersion(publicFlyway) + "\",\"governanceVersion\":\""
                    + currentVersion(governanceFlyway) + "\"}");
            return 0;
        } catch (RuntimeException exception) {
            error.println("DATABASE_MIGRATION_FAILED");
            return 1;
        } finally {
            if (context != null) {
                ConfigurableApplicationContext contextToClose = context;
                try {
                    runWithFrameworkOutputSuppressed(() -> {
                        contextToClose.close();
                        return null;
                    });
                } catch (RuntimeException ignored) {
                    // The command result is final and shutdown diagnostics stay redacted.
                }
            }
        }
    }

    static String[] applicationArguments() {
        return new String[] {
            "--spring.main.web-application-type=none",
            "--spring.main.banner-mode=off",
            "--spring.main.log-startup-info=false",
            "--logging.level.root=OFF",
            "--portfolio.database.public.enabled=true",
            "--portfolio.database.governance.enabled=true"
        };
    }

    private static String currentVersion(Flyway flyway) {
        MigrationInfo current = flyway.info().current();
        return current == null || current.getVersion() == null
                ? "none"
                : current.getVersion().getVersion();
    }

    private static <T> T runWithFrameworkOutputSuppressed(FrameworkCommand<T> command) {
        synchronized (FRAMEWORK_IO_MONITOR) {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            try (PrintStream sink = new PrintStream(OutputStream.nullOutputStream())) {
                System.setOut(sink);
                System.setErr(sink);
                return command.run();
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
    }

    @FunctionalInterface
    private interface FrameworkCommand<T> {
        T run();
    }
}
