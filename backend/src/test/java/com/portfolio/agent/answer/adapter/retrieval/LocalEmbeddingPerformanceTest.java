package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEmbeddingPerformanceTest {
    private static final int WARMUP_RUNS = 20;
    private static final int MEASURED_RUNS = 100;
    private static final long MAX_P95_MILLIS = 500;
    private static final long MAX_COMMITTED_DELTA_MEGABYTES = 350;
    private static final String FIXED_PUBLIC_QUERY = "这个项目交付了什么？";
    private static final Pattern COMMITTED_KB = Pattern.compile(
            "Total: reserved=\\d+KB, committed=(\\d+)KB");

    @Test
    void localInt8ModelMeetsTheFourCoreFourGigabyteAdmissionBudget() throws Exception {
        String configured = System.getProperty("portfolio.embedding.modelDir", "").strip();
        Assumptions.assumeTrue(!configured.isEmpty());
        Assumptions.assumeTrue(isNativeMemoryTrackingAvailable());
        Path modelDirectory = Path.of(configured).toAbsolutePath().normalize();
        LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                .verify(modelDirectory);
        long baselineCommittedKb = committedNativeMemoryKb();
        List<Long> elapsedMillis = new ArrayList<>();
        int successes = 0;

        try (OnnxLocalEmbeddingAdapter adapter = new OnnxLocalEmbeddingAdapter(
                modelDirectory, artifact.getQueryInstruction(), artifact.getMaxTokens(),
                artifact.getDimension(), artifact.getIntraOpThreads(),
                artifact.getInterOpThreads())) {
            for (int index = 0; index < WARMUP_RUNS; index++) {
                adapter.embedQuery(FIXED_PUBLIC_QUERY);
            }
            for (int index = 0; index < MEASURED_RUNS; index++) {
                long started = System.nanoTime();
                EmbeddingVector vector = adapter.embedQuery(FIXED_PUBLIC_QUERY);
                elapsedMillis.add(TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - started));
                if (vector.dimension() == artifact.getDimension()) {
                    successes++;
                }
            }
            long modelCommittedKb = committedNativeMemoryKb();
            Collections.sort(elapsedMillis);
            long p50 = percentile(elapsedMillis, 0.50d);
            long p95 = percentile(elapsedMillis, 0.95d);
            long deltaMb = Math.max(0L, modelCommittedKb - baselineCommittedKb) / 1024L;

            System.out.printf(
                    "LOCAL_RETRIEVAL_BENCHMARK p50Ms=%d p95Ms=%d committedDeltaMb=%d successes=%d%n",
                    p50, p95, deltaMb, successes);
            assertThat(successes).isEqualTo(MEASURED_RUNS);
            assertThat(p95).isLessThanOrEqualTo(MAX_P95_MILLIS);
            assertThat(deltaMb).isLessThanOrEqualTo(MAX_COMMITTED_DELTA_MEGABYTES);
        }
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private boolean isNativeMemoryTrackingAvailable() throws Exception {
        return jcmdOutput().contains("Native Memory Tracking");
    }

    private long committedNativeMemoryKb() throws Exception {
        Matcher matcher = COMMITTED_KB.matcher(jcmdOutput());
        if (!matcher.find()) {
            throw new IllegalStateException("NATIVE_MEMORY_SUMMARY_UNAVAILABLE");
        }
        return Long.parseLong(matcher.group(1));
    }

    private String jcmdOutput() throws IOException, InterruptedException {
        String executable = System.getProperty("os.name", "").startsWith("Windows")
                ? "jcmd.exe" : "jcmd";
        Path jcmd = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(
                jcmd.toString(), Long.toString(ProcessHandle.current().pid()),
                "VM.native_memory", "summary", "scale=KB")
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new IllegalStateException("NATIVE_MEMORY_COMMAND_FAILED");
        }
        return new String(output, StandardCharsets.UTF_8);
    }
}
