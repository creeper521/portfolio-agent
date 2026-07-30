package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.PortfolioAgentApplication;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleDatabaseImporter;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleImportResult;
import com.portfolio.agent.portfolio.service.PublicReleaseActivationResult;
import com.portfolio.agent.portfolio.service.PublicReleaseActivationService;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class PublicBundleDatabaseImportCli {

    private static final Object FRAMEWORK_IO_MONITOR = new Object();

    private final BundleCommandLoader loader;
    private final ImportCommand importer;
    private final ActivationCommand activation;
    private final PrintStream output;
    private final PrintStream error;

    public PublicBundleDatabaseImportCli(
            BundleCommandLoader loader,
            ImportCommand importer,
            ActivationCommand activation,
            PrintStream output,
            PrintStream error) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.activation = Objects.requireNonNull(activation, "activation");
        this.output = Objects.requireNonNull(output, "output");
        this.error = Objects.requireNonNull(error, "error");
    }

    public static void main(String[] args) {
        int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        return execute(args, out, err, PublicBundleDatabaseImportCli::launchContext);
    }

    static int execute(
            String[] args,
            PrintStream out,
            PrintStream err,
            ContextLauncher contextLauncher) {
        ParsedCommand parsed = parse(args);
        if (parsed == null) {
            err.println("PUBLIC_BUNDLE_INVALID_ARGUMENTS");
            return 2;
        }
        if (parsed.action == Action.VERIFY) {
            PublicBundleDatabaseImportCli cli = new PublicBundleDatabaseImportCli(
                    PublicBundleDatabaseImportCli::load,
                    snapshot -> {
                        throw new UnsupportedOperationException("import is unavailable");
                    },
                    releaseId -> {
                        throw new UnsupportedOperationException("activation is unavailable");
                    },
                    out,
                    err);
            return cli.run(args);
        }
        ConfigurableApplicationContext context = null;
        try {
            context = runWithFrameworkOutputSuppressed(
                    () -> contextLauncher.launch(applicationArguments(args)));
            ConfigurableApplicationContext activeContext = context;
            RequiredBeans beans = runWithFrameworkOutputSuppressed(() ->
                    new RequiredBeans(
                            activeContext.getBean(PublicBundleDatabaseImporter.class),
                            activeContext.getBean(PublicReleaseActivationService.class)));
            PublicBundleDatabaseImportCli cli = new PublicBundleDatabaseImportCli(
                    PublicBundleDatabaseImportCli::load,
                    beans.importer::importBundle,
                    beans.activation::activate,
                    out,
                    err);
            return runWithFrameworkOutputSuppressed(() -> cli.run(args));
        } catch (RuntimeException exception) {
            err.println("PUBLIC_BUNDLE_COMMAND_FAILED");
            return 1;
        } finally {
            ConfigurableApplicationContext contextToClose = context;
            if (contextToClose != null) {
                try {
                    runWithFrameworkOutputSuppressed(() -> {
                        contextToClose.close();
                        return null;
                    });
                } catch (RuntimeException ignored) {
                    // The command result is already final; shutdown diagnostics stay redacted.
                }
            }
        }
    }

    static String[] applicationArguments(String[] arguments) {
        String[] supplied = arguments == null ? new String[0] : arguments;
        String[] result = Arrays.copyOf(supplied, supplied.length + 5);
        result[supplied.length] = "--spring.main.web-application-type=none";
        result[supplied.length + 1] = "--spring.main.banner-mode=off";
        result[supplied.length + 2] = "--spring.main.log-startup-info=false";
        result[supplied.length + 3] = "--logging.level.root=OFF";
        result[supplied.length + 4] = "--portfolio.database.public.enabled=true";
        return result;
    }

    private static ConfigurableApplicationContext launchContext(String[] arguments) {
        return new SpringApplicationBuilder(PortfolioAgentApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .registerShutdownHook(false)
                .run(arguments);
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

    public int run(String[] args) {
        ParsedCommand command = parse(args);
        if (command == null) {
            error.println("PUBLIC_BUNDLE_INVALID_ARGUMENTS");
            return 2;
        }
        try {
            return switch (command.action) {
                case VERIFY -> verify(command.bundle);
                case IMPORT -> importBundle(command.bundle);
                case ACTIVATE -> activate(command.releaseId);
            };
        } catch (RuntimeException | java.io.IOException exception) {
            error.println(failureCode(command.action));
            return 1;
        }
    }

    private int verify(Path bundle) throws java.io.IOException {
        RuntimeContentSnapshot snapshot = loader.load(bundle);
        printBundleResult("VERIFY", null, snapshot.getContentVersion(), "VERIFIED", snapshot);
        return 0;
    }

    private int importBundle(Path bundle) throws java.io.IOException {
        RuntimeContentSnapshot snapshot = loader.load(bundle);
        PublicBundleImportResult result = importer.importBundle(snapshot);
        printBundleResult(
                "IMPORT",
                result.getReleaseId(),
                result.getReleaseVersion(),
                result.getReleaseStatus(),
                snapshot);
        return 0;
    }

    private int activate(String releaseId) {
        PublicReleaseActivationResult result = activation.activate(releaseId);
        output.println("{\"action\":\"ACTIVATE\",\"releaseId\":"
                + json(result.getReleaseId()) + ",\"status\":"
                + json(result.getReleaseStatus()) + "}");
        return 0;
    }

    private void printBundleResult(
            String action,
            String releaseId,
            String releaseVersion,
            String status,
            RuntimeContentSnapshot snapshot) {
        StringBuilder value = new StringBuilder("{\"action\":")
                .append(json(action));
        if (releaseId != null) {
            value.append(",\"releaseId\":").append(json(releaseId));
        }
        value.append(",\"releaseVersion\":").append(json(releaseVersion))
                .append(",\"status\":").append(json(status))
                .append(",\"projects\":").append(snapshot.getProjects().size())
                .append(",\"cases\":").append(snapshot.getCases().size())
                .append(",\"claims\":").append(snapshot.getClaims().size())
                .append(",\"evidence\":").append(snapshot.getApprovedEvidence().size())
                .append('}');
        output.println(value);
    }

    private static ParsedCommand parse(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        if ("verify".equals(args[0]) || "import".equals(args[0])) {
            if (args.length != 3 || !"--bundle".equals(args[1])
                    || args[2] == null || args[2].isBlank()) {
                return null;
            }
            try {
                return new ParsedCommand(
                        "verify".equals(args[0]) ? Action.VERIFY : Action.IMPORT,
                        Path.of(args[2]),
                        null);
            } catch (RuntimeException exception) {
                return null;
            }
        }
        if ("activate".equals(args[0])) {
            if (args.length != 5) {
                return null;
            }
            String releaseId = null;
            String confirmation = null;
            for (int index = 1; index < args.length; index += 2) {
                if (index + 1 >= args.length) {
                    return null;
                }
                if ("--release-id".equals(args[index]) && releaseId == null) {
                    releaseId = args[index + 1];
                } else if ("--confirm-release-id".equals(args[index])
                        && confirmation == null) {
                    confirmation = args[index + 1];
                } else {
                    return null;
                }
            }
            if (releaseId == null || !releaseId.equals(confirmation)
                    || !isUuid(releaseId)) {
                return null;
            }
            return new ParsedCommand(Action.ACTIVATE, null, releaseId);
        }
        return null;
    }

    private static RuntimeContentSnapshot load(Path bundle)
            throws java.io.IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new PublicBundleLoader(
                mapper,
                new PortfolioSnapshotValidator(),
                Clock.systemUTC())
                .load(new PublicBundleDirectoryReader().read(bundle));
    }

    private String failureCode(Action action) {
        return switch (action) {
            case VERIFY -> "PUBLIC_BUNDLE_VERIFICATION_FAILED";
            case IMPORT -> "PUBLIC_BUNDLE_IMPORT_FAILED";
            case ACTIVATE -> "PUBLIC_RELEASE_ACTIVATION_FAILED";
        };
    }

    private static boolean isUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equals(
                    value.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String json(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }

    @FunctionalInterface
    public interface BundleCommandLoader {
        RuntimeContentSnapshot load(Path bundle) throws java.io.IOException;
    }

    @FunctionalInterface
    public interface ImportCommand {
        PublicBundleImportResult importBundle(RuntimeContentSnapshot snapshot);
    }

    @FunctionalInterface
    public interface ActivationCommand {
        PublicReleaseActivationResult activate(String releaseId);
    }

    @FunctionalInterface
    interface ContextLauncher {
        ConfigurableApplicationContext launch(String[] arguments);
    }

    @FunctionalInterface
    private interface FrameworkCommand<T> {
        T run();
    }

    private enum Action {
        VERIFY,
        IMPORT,
        ACTIVATE
    }

    private static final class ParsedCommand {
        private final Action action;
        private final Path bundle;
        private final String releaseId;

        private ParsedCommand(Action action, Path bundle, String releaseId) {
            this.action = action;
            this.bundle = bundle;
            this.releaseId = releaseId;
        }
    }

    private static final class RequiredBeans {
        private final PublicBundleDatabaseImporter importer;
        private final PublicReleaseActivationService activation;

        private RequiredBeans(
                PublicBundleDatabaseImporter importer,
                PublicReleaseActivationService activation) {
            this.importer = Objects.requireNonNull(importer, "importer");
            this.activation = Objects.requireNonNull(activation, "activation");
        }
    }
}
