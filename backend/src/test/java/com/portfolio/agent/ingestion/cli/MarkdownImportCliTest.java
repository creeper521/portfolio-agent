package com.portfolio.agent.ingestion.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.ingestion.domain.MarkdownImportReport;
import com.portfolio.agent.ingestion.domain.MarkdownScanEntry;
import com.portfolio.agent.ingestion.domain.MarkdownScanReport;
import com.portfolio.agent.ingestion.domain.SourceDocumentStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkdownImportCliTest {

    @TempDir
    Path root;

    @Test
    void dryRunPrintsStructuredCountsWithoutPrivateTextOrAbsoluteRoot() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MarkdownImportCli cli = new MarkdownImportCli(
                path -> new MarkdownScanReport(List.of(new MarkdownScanEntry(
                        "private.md", SourceDocumentStatus.ADDED, "hash", null))),
                path -> new MarkdownImportReport(1, 0, 0, 0, 0, 0, 0),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                System.err);

        int status = cli.run(new String[] {"scan", "--root", root.toString(), "--dry-run"});

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(status).isZero();
        assertThat(rendered).contains("\"mode\":\"DRY_RUN\"").contains("\"added\":1")
                .contains("\"vectorPending\":0").contains("\"partial\":false");
        assertThat(rendered).doesNotContain("private.md").doesNotContain(root.toString());
    }

    @Test
    void suppliesGovernanceEnablementAsACommandLineArgumentAboveApplicationDefault() {
        String[] arguments = MarkdownImportCli.applicationArguments(new String[] {
                "import", "--root", "relative",
                "--portfolio.database.governance.enabled=false"
        });

        assertThat(arguments)
                .contains("--spring.main.web-application-type=none")
                .contains("--spring.main.banner-mode=off")
                .contains("--spring.main.log-startup-info=false")
                .contains("--logging.level.root=OFF")
                .endsWith("--portfolio.database.governance.enabled=true");
    }

    @Test
    void productionImportSuppressesFrameworkAndModelOutputAndClosesContext() {
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);
        com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore store =
                Mockito.mock(com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore.class);
        com.portfolio.agent.ingestion.service.MarkdownImportService importer =
                Mockito.mock(com.portfolio.agent.ingestion.service.MarkdownImportService.class);
        when(context.getBean(com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore.class))
                .thenReturn(store);
        when(context.getBean(com.portfolio.agent.ingestion.service.MarkdownImportService.class))
                .thenReturn(importer);
        when(importer.importRoot(Mockito.any())).thenAnswer(invocation -> {
            System.out.println("model C:\\private\\knowledge.md");
            System.err.println("password=hunter2 jdbc:postgresql://secret-host/private");
            return new MarkdownImportReport(1, 0, 0, 0, 0, 0, 0);
        });
        Mockito.doAnswer(invocation -> {
            System.out.println("Flyway close C:\\private\\knowledge.md");
            System.err.println("Hikari password=never-print");
            throw new IllegalStateException("shutdown jdbc:postgresql://secret-host/private");
        }).when(context).close();

        CapturedProcess process = captureProcess(() -> MarkdownImportCli.execute(
                new String[] {"import", "--root", root.toString()},
                System.out,
                System.err,
                arguments -> {
                    assertThat(arguments)
                            .contains("--spring.main.web-application-type=none")
                            .contains("--spring.main.banner-mode=off")
                            .contains("--logging.level.root=OFF")
                            .endsWith("--portfolio.database.governance.enabled=true");
                    System.out.println("Spring banner C:\\private\\knowledge.md");
                    System.err.println("Hikari jdbc:postgresql://secret-host password=hunter2");
                    return context;
                }));

        assertThat(process.exitCode).isZero();
        assertThat(process.out.trim()).startsWith("{\"mode\":\"IMPORT\"")
                .doesNotContain("Spring", "Flyway", "Hikari", "jdbc:", "secret", "password",
                        root.toString());
        assertThat(process.out.trim().lines().count()).isEqualTo(1);
        assertThat(process.err).isEmpty();
        verify(context).getBean(com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore.class);
        verify(context).getBean(com.portfolio.agent.ingestion.service.MarkdownImportService.class);
        verify(context).close();
    }

    @Test
    void productionStartupAndBeanFailuresPrintOnlyStableRedactedCode() {
        CapturedProcess startup = captureProcess(() -> MarkdownImportCli.execute(
                new String[] {"scan", "--root", root.toString(), "--dry-run"},
                System.out,
                System.err,
                arguments -> {
                    System.out.println("diagnostic " + root);
                    System.err.println("jdbc:postgresql://secret-host password=hunter2");
                    throw new IllegalStateException("credential at " + root);
                }));

        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);
        when(context.getBean(com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore.class))
                .thenAnswer(invocation -> {
                    System.out.println("bean lookup " + root);
                    throw new IllegalStateException("model path " + root);
                });
        CapturedProcess beanLookup = captureProcess(() -> MarkdownImportCli.execute(
                new String[] {"import", "--root", root.toString()},
                System.out,
                System.err,
                arguments -> context));

        assertThat(startup.exitCode).isEqualTo(1);
        assertThat(startup.out).isEmpty();
        assertThat(startup.err.trim()).isEqualTo("MARKDOWN_COMMAND_FAILED");
        assertThat(beanLookup.exitCode).isEqualTo(1);
        assertThat(beanLookup.out).isEmpty();
        assertThat(beanLookup.err.trim()).isEqualTo("MARKDOWN_COMMAND_FAILED")
                .doesNotContain(root.toString(), "jdbc:", "password", "model");
        verify(context).close();
    }

    @Test
    void productionEmbeddingFailurePrintsOnlyStableRedactedCode() {
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);
        com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore store =
                Mockito.mock(com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore.class);
        com.portfolio.agent.ingestion.service.MarkdownImportService importer =
                Mockito.mock(com.portfolio.agent.ingestion.service.MarkdownImportService.class);
        when(context.getBean(com.portfolio.agent.ingestion.gateway.MarkdownGovernanceStore.class))
                .thenReturn(store);
        when(context.getBean(com.portfolio.agent.ingestion.service.MarkdownImportService.class))
                .thenReturn(importer);
        when(importer.importRoot(Mockito.any())).thenAnswer(invocation -> {
            System.out.println("ONNX model " + root);
            System.err.println("embedding failure password=hunter2");
            throw new IllegalStateException("model directory " + root);
        });

        CapturedProcess process = captureProcess(() -> MarkdownImportCli.execute(
                new String[] {"import", "--root", root.toString()},
                System.out,
                System.err,
                arguments -> context));

        assertThat(process.exitCode).isEqualTo(1);
        assertThat(process.out).isEmpty();
        assertThat(process.err.trim()).isEqualTo("MARKDOWN_COMMAND_FAILED")
                .doesNotContain(root.toString(), "ONNX", "embedding", "password");
        verify(context).close();
    }

    @Test
    void invalidArgumentsAreRedactedWithoutStartingSpring() {
        CapturedProcess process = captureProcess(() -> MarkdownImportCli.execute(
                new String[] {"import", "--root", "invalid\u0000path"},
                System.out,
                System.err,
                arguments -> {
                    throw new AssertionError("must not launch");
                }));

        assertThat(process.exitCode).isEqualTo(2);
        assertThat(process.out).isEmpty();
        assertThat(process.err.trim()).isEqualTo("MARKDOWN_INVALID_ARGUMENTS");
    }

    private CapturedProcess captureProcess(ExitCommand command) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            return new CapturedProcess(
                    command.run(),
                    out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @FunctionalInterface
    private interface ExitCommand {
        int run();
    }

    private static final class CapturedProcess {
        private final int exitCode;
        private final String out;
        private final String err;

        private CapturedProcess(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }
}
