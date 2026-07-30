package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleDatabaseImporter;
import com.portfolio.agent.portfolio.repository.postgres.PublicBundleImportResult;
import com.portfolio.agent.portfolio.service.PublicReleaseActivationResult;
import com.portfolio.agent.portfolio.service.PublicReleaseActivationService;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicBundleDatabaseImportCliTest {

    private static final String RELEASE_ID = "5e6c7a8b-1234-4abc-9def-1234567890ab";

    @Test
    void verifiesWithoutMutatingDatabase() throws Exception {
        RuntimeContentSnapshot snapshot = loadFixture();
        PublicBundleDatabaseImporter importer = Mockito.mock(PublicBundleDatabaseImporter.class);
        PublicReleaseActivationService activation = Mockito.mock(PublicReleaseActivationService.class);

        RunResult result = run(snapshot, importer, activation,
                "verify", "--bundle", fixtureDirectory().toString());

        assertThat(result.exitCode).isZero();
        assertThat(result.err).isEmpty();
        assertThat(result.out).isEqualTo(
                "{\"action\":\"VERIFY\",\"releaseVersion\":\"2026-07-29.1\","
                        + "\"status\":\"VERIFIED\",\"projects\":5,\"cases\":49,"
                        + "\"claims\":79,\"evidence\":59}");
        verify(importer, never()).importBundle(Mockito.any());
        verify(activation, never()).activate(Mockito.anyString());
    }

    @Test
    void importsVerifiedReleaseWithoutActivatingIt() throws Exception {
        RuntimeContentSnapshot snapshot = loadFixture();
        PublicBundleDatabaseImporter importer = Mockito.mock(PublicBundleDatabaseImporter.class);
        PublicReleaseActivationService activation = Mockito.mock(PublicReleaseActivationService.class);
        when(importer.importBundle(snapshot)).thenReturn(
                new PublicBundleImportResult(RELEASE_ID, "2026-07-29.1", "VERIFIED"));

        RunResult result = run(snapshot, importer, activation,
                "import", "--bundle", fixtureDirectory().toString());

        assertThat(result.exitCode).isZero();
        assertThat(result.out).contains(
                "\"action\":\"IMPORT\"",
                "\"releaseId\":\"" + RELEASE_ID + "\"",
                "\"status\":\"VERIFIED\"",
                "\"projects\":5",
                "\"cases\":49",
                "\"claims\":79",
                "\"evidence\":59");
        verify(importer).importBundle(snapshot);
        verify(activation, never()).activate(Mockito.anyString());
    }

    @Test
    void activatesOnlyWithExactUuidConfirmation() throws Exception {
        RuntimeContentSnapshot snapshot = loadFixture();
        PublicBundleDatabaseImporter importer = Mockito.mock(PublicBundleDatabaseImporter.class);
        PublicReleaseActivationService activation = Mockito.mock(PublicReleaseActivationService.class);
        when(activation.activate(RELEASE_ID)).thenReturn(
                new PublicReleaseActivationResult(RELEASE_ID, "PUBLISHED"));

        RunResult success = run(snapshot, importer, activation,
                "activate", "--release-id", RELEASE_ID, "--confirm-release-id", RELEASE_ID);
        RunResult mismatch = run(snapshot, importer, activation,
                "activate", "--release-id", RELEASE_ID,
                "--confirm-release-id", "6e6c7a8b-1234-4abc-9def-1234567890ab");
        RunResult malformed = run(snapshot, importer, activation,
                "activate", "--release-id", "not-a-uuid", "--confirm-release-id", "not-a-uuid");
        RunResult nonCanonical = run(snapshot, importer, activation,
                "activate", "--release-id", "1-1-1-1-1",
                "--confirm-release-id", "1-1-1-1-1");

        assertThat(success.exitCode).isZero();
        assertThat(success.out).isEqualTo(
                "{\"action\":\"ACTIVATE\",\"releaseId\":\"" + RELEASE_ID
                        + "\",\"status\":\"PUBLISHED\"}");
        assertThat(mismatch.exitCode).isEqualTo(2);
        assertThat(malformed.exitCode).isEqualTo(2);
        assertThat(nonCanonical.exitCode).isEqualTo(2);
        verify(activation).activate(RELEASE_ID);
    }

    @Test
    void rejectsUnknownArgumentsAndRedactsRuntimeFailures() throws Exception {
        RuntimeContentSnapshot snapshot = loadFixture();
        PublicBundleDatabaseImporter importer = Mockito.mock(PublicBundleDatabaseImporter.class);
        PublicReleaseActivationService activation = Mockito.mock(PublicReleaseActivationService.class);
        when(importer.importBundle(snapshot)).thenThrow(
                new IllegalStateException("jdbc:postgresql://secret-host/password"));

        RunResult unknown = run(snapshot, importer, activation,
                "import", "--bundle", fixtureDirectory().toString(), "--surprise");
        RunResult malformedPath = run(snapshot, importer, activation,
                "import", "--bundle", "invalid\u0000path");
        RunResult failed = run(snapshot, importer, activation,
                "import", "--bundle", fixtureDirectory().toString());

        assertThat(unknown.exitCode).isEqualTo(2);
        assertThat(unknown.out).isEmpty();
        assertThat(unknown.err).isEqualTo("PUBLIC_BUNDLE_INVALID_ARGUMENTS");
        assertThat(malformedPath.exitCode).isEqualTo(2);
        assertThat(malformedPath.err).isEqualTo("PUBLIC_BUNDLE_INVALID_ARGUMENTS");
        assertThat(failed.exitCode).isEqualTo(1);
        assertThat(failed.out).isEmpty();
        assertThat(failed.err).isEqualTo("PUBLIC_BUNDLE_IMPORT_FAILED")
                .doesNotContain("secret-host", "password", fixtureDirectory().toString());
    }

    @Test
    void applicationArgumentsForcePublicDatabaseAtHighestCommandLinePrecedence() {
        String[] arguments = PublicBundleDatabaseImportCli.applicationArguments(new String[] {
                "import", "--bundle", "bundle", "--portfolio.database.public.enabled=false"
        });

        assertThat(arguments).endsWith("--portfolio.database.public.enabled=true");
    }

    @Test
    void productionVerifyRunsWithoutStartingOrMutatingPublicDatabase() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = PublicBundleDatabaseImportCli.execute(
                new String[] {"verify", "--bundle", fixtureDirectory().toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("\"action\":\"VERIFY\"");
    }

    @Test
    void productionImportSuppressesFrameworkOutputAndObtainsRequiredBeans()
            throws Exception {
        PublicBundleDatabaseImporter importer = Mockito.mock(PublicBundleDatabaseImporter.class);
        PublicReleaseActivationService activation = Mockito.mock(PublicReleaseActivationService.class);
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);
        when(context.getBean(PublicBundleDatabaseImporter.class)).thenReturn(importer);
        when(context.getBean(PublicReleaseActivationService.class)).thenReturn(activation);
        when(importer.importBundle(Mockito.any())).thenAnswer(invocation -> {
            System.out.println("JDBC driver C:\\private\\bundle");
            System.err.println("database password=hunter2");
            return new PublicBundleImportResult(
                    RELEASE_ID, "2026-07-29.1", "VERIFIED");
        });
        Mockito.doAnswer(invocation -> {
            System.out.println("Flyway jdbc:postgresql://secret-host/private");
            System.err.println("password=never-print");
            return null;
        }).when(context).close();
        CapturedProcess process = captureProcess(() -> PublicBundleDatabaseImportCli.execute(
                new String[] {"import", "--bundle", fixtureDirectory().toString()},
                System.out,
                System.err,
                arguments -> {
                    assertThat(arguments)
                            .contains("--spring.main.web-application-type=none")
                            .contains("--spring.main.banner-mode=off")
                            .contains("--logging.level.root=OFF")
                            .endsWith("--portfolio.database.public.enabled=true");
                    System.out.println("Spring banner secret-path");
                    System.err.println("Hikari jdbc:postgresql://secret-host/password");
                    return context;
                }));

        assertThat(process.exitCode).isZero();
        assertThat(process.out.trim()).startsWith("{\"action\":\"IMPORT\"")
                .doesNotContain("Spring", "Flyway", "Hikari", "jdbc:", "secret", "password");
        assertThat(process.out.trim().lines().count()).isEqualTo(1);
        assertThat(process.err).isEmpty();
        verify(context).getBean(PublicBundleDatabaseImporter.class);
        verify(context).getBean(PublicReleaseActivationService.class);
        verify(context).close();
    }

    @Test
    void productionStartupFailurePrintsOnlyStableRedactedCode() {
        CapturedProcess process = captureProcess(() -> PublicBundleDatabaseImportCli.execute(
                new String[] {"activate", "--release-id", RELEASE_ID,
                        "--confirm-release-id", RELEASE_ID},
                System.out,
                System.err,
                arguments -> {
                    System.out.println("ApplicationStartupDiagnostics C:\\private\\bundle");
                    System.err.println("jdbc:postgresql://secret-host password=hunter2");
                    throw new IllegalStateException(
                            "cannot connect jdbc:postgresql://secret-host password=hunter2");
                }));

        assertThat(process.exitCode).isEqualTo(1);
        assertThat(process.out).isEmpty();
        assertThat(process.err.trim()).isEqualTo("PUBLIC_BUNDLE_COMMAND_FAILED")
                .doesNotContain("jdbc:", "secret-host", "hunter2", "private");
        assertThat(process.err.trim().lines().count()).isEqualTo(1);
    }

    private RunResult run(
            RuntimeContentSnapshot snapshot,
            PublicBundleDatabaseImporter importer,
            PublicReleaseActivationService activation,
            String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PublicBundleDatabaseImportCli cli = new PublicBundleDatabaseImportCli(
                path -> snapshot, importer::importBundle, activation::activate,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        int exitCode = cli.run(args);
        return new RunResult(
                exitCode,
                out.toString(StandardCharsets.UTF_8).trim(),
                err.toString(StandardCharsets.UTF_8).trim());
    }

    private RuntimeContentSnapshot loadFixture() throws Exception {
        return new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(),
                Clock.systemUTC())
                .load(new PublicBundleDirectoryReader().read(fixtureDirectory()));
    }

    private Path fixtureDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path root = Files.isDirectory(current.resolve("backend")) ? current : current.getParent();
        return root.resolve("backend/src/main/resources/public-data/bundle");
    }

    private CapturedProcess captureProcess(ExitCommand command) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int exitCode = command.run();
            return new CapturedProcess(
                    exitCode,
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

    private static final class RunResult {
        private final int exitCode;
        private final String out;
        private final String err;

        private RunResult(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }
}
