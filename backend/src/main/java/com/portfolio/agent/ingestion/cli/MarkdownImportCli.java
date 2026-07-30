package com.portfolio.agent.ingestion.cli;

import com.portfolio.agent.PortfolioAgentApplication;
import com.portfolio.agent.ingestion.domain.MarkdownImportReport;
import com.portfolio.agent.ingestion.domain.MarkdownScanEntry;
import com.portfolio.agent.ingestion.domain.MarkdownScanReport;
import com.portfolio.agent.ingestion.domain.SourceDocumentStatus;
import com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore;
import com.portfolio.agent.ingestion.service.MarkdownImportService;
import com.portfolio.agent.ingestion.service.MarkdownScanService;
import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class MarkdownImportCli {

    private static final Object FRAMEWORK_IO_MONITOR = new Object();

    private final ScanCommand scanCommand;
    private final ImportCommand importCommand;
    private final PrintStream output;
    private final PrintStream error;

    public MarkdownImportCli(
            ScanCommand scanCommand,
            ImportCommand importCommand,
            PrintStream output,
            PrintStream error) {
        this.scanCommand = Objects.requireNonNull(scanCommand, "scanCommand");
        this.importCommand = Objects.requireNonNull(importCommand, "importCommand");
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
        return execute(args, out, err, MarkdownImportCli::launchContext);
    }

    static int execute(
            String[] args,
            PrintStream out,
            PrintStream err,
            ContextLauncher contextLauncher) {
        if (parse(args) == null) {
            err.println("MARKDOWN_INVALID_ARGUMENTS");
            return 2;
        }
        ConfigurableApplicationContext context = null;
        try {
            context = runWithFrameworkOutputSuppressed(
                    () -> contextLauncher.launch(applicationArguments(args)));
            ConfigurableApplicationContext activeContext = context;
            RequiredBeans beans = runWithFrameworkOutputSuppressed(() ->
                    new RequiredBeans(
                            activeContext.getBean(MarkdownGovernanceStore.class),
                            activeContext.getBean(MarkdownImportService.class)));
            MarkdownImportCli cli = new MarkdownImportCli(
                    new MarkdownScanService(beans.store)::scan,
                    beans.importer::importRoot,
                    out,
                    err);
            return runWithFrameworkOutputSuppressed(() -> cli.run(args));
        } catch (RuntimeException exception) {
            err.println("MARKDOWN_COMMAND_FAILED");
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
                    // Command outcome is final; shutdown diagnostics stay redacted.
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
        result[supplied.length + 4] = "--portfolio.database.governance.enabled=true";
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
            error.println("MARKDOWN_INVALID_ARGUMENTS");
            return 2;
        }
        try {
            if (command.dryRun()) {
                printScan(scanCommand.scan(command.root()));
            } else {
                printImport(importCommand.importRoot(command.root()));
            }
            return 0;
        } catch (RuntimeException exception) {
            error.println("MARKDOWN_COMMAND_FAILED");
            return 1;
        }
    }

    private static ParsedCommand parse(String[] args) {
        if (args == null || args.length < 3) {
            return null;
        }
        String action = args[0];
        Path root = null;
        boolean dryRun = false;
        for (int index = 1; index < args.length; index++) {
            if ("--root".equals(args[index]) && index + 1 < args.length) {
                try {
                    root = Path.of(args[++index]);
                } catch (RuntimeException exception) {
                    return null;
                }
            } else if ("--dry-run".equals(args[index])) {
                dryRun = true;
            } else {
                return null;
            }
        }
        if (root == null) {
            return null;
        }
        if ("scan".equals(action) && dryRun) {
            return new ParsedCommand(root, true);
        }
        if ("import".equals(action) && !dryRun) {
            return new ParsedCommand(root, false);
        }
        return null;
    }

    private void printScan(MarkdownScanReport report) {
        int added = 0;
        int changed = 0;
        int unchanged = 0;
        int missing = 0;
        int failed = 0;
        int blocked = 0;
        for (MarkdownScanEntry entry : report.getEntries()) {
            SourceDocumentStatus status = entry.getStatus();
            switch (status) {
                case ADDED -> added++;
                case CHANGED -> changed++;
                case UNCHANGED -> unchanged++;
                case MISSING -> missing++;
                case FAILED -> failed++;
                case BLOCKED -> blocked++;
            }
        }
        print("DRY_RUN", added, changed, unchanged, missing, failed, blocked, 0, false);
    }

    private void printImport(MarkdownImportReport report) {
        print("IMPORT", report.getAdded(), report.getChanged(), report.getUnchanged(), report.getMissing(),
                report.getFailed(), report.getBlocked(), report.getVectorPending(), report.isPartial());
    }

    private void print(
            String mode, int added, int changed, int unchanged, int missing, int failed, int blocked,
            int vectorPending, boolean partial) {
        output.println("{\"mode\":\"" + mode + "\",\"added\":" + added
                + ",\"changed\":" + changed
                + ",\"unchanged\":" + unchanged
                + ",\"missing\":" + missing
                + ",\"failed\":" + failed
                + ",\"blocked\":" + blocked
                + ",\"vectorPending\":" + vectorPending
                + ",\"partial\":" + partial + "}");
    }

    @FunctionalInterface
    public interface ScanCommand {
        MarkdownScanReport scan(Path root);
    }

    @FunctionalInterface
    public interface ImportCommand {
        MarkdownImportReport importRoot(Path root);
    }

    @FunctionalInterface
    interface ContextLauncher {
        ConfigurableApplicationContext launch(String[] arguments);
    }

    @FunctionalInterface
    private interface FrameworkCommand<T> {
        T run();
    }

    private static final class ParsedCommand {
        private final Path root;
        private final boolean dryRun;

        private ParsedCommand(Path root, boolean dryRun) {
            this.root = root;
            this.dryRun = dryRun;
        }

        private Path root() { return root; }
        private boolean dryRun() { return dryRun; }
    }

    private static final class RequiredBeans {
        private final MarkdownGovernanceStore store;
        private final MarkdownImportService importer;

        private RequiredBeans(
                MarkdownGovernanceStore store,
                MarkdownImportService importer) {
            this.store = Objects.requireNonNull(store, "store");
            this.importer = Objects.requireNonNull(importer, "importer");
        }
    }
}
