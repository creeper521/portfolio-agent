package com.portfolio.agent.release.benchmark;

import java.time.Instant;
import java.util.Objects;

public final class RetrievalBenchmarkRunMetadata {

    private final String javaVersion;
    private final String javaRuntimeName;
    private final String javaVendor;
    private final String osName;
    private final String osVersion;
    private final String osArch;
    private final int availableProcessors;
    private final Instant startedAt;
    private final Instant completedAt;
    private final long durationMillis;
    private final String suiteVersion;
    private final String contentVersion;
    private final String runtimeBundleHash;
    private final String policyVersion;
    private final String modelId;
    private final String modelDescriptorHash;
    private final int modelDimension;

    public RetrievalBenchmarkRunMetadata(
            String javaVersion,
            String javaRuntimeName,
            String javaVendor,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            Instant startedAt,
            Instant completedAt,
            long durationMillis,
            String suiteVersion,
            String contentVersion,
            String runtimeBundleHash,
            String policyVersion,
            String modelId,
            String modelDescriptorHash,
            int modelDimension
    ) {
        this.javaVersion = required(javaVersion, "javaVersion");
        this.javaRuntimeName = required(javaRuntimeName, "javaRuntimeName");
        this.javaVendor = required(javaVendor, "javaVendor");
        this.osName = required(osName, "osName");
        this.osVersion = required(osVersion, "osVersion");
        this.osArch = required(osArch, "osArch");
        if (availableProcessors < 1) {
            throw new IllegalArgumentException(
                    "availableProcessors must be positive");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException(
                    "durationMillis must not be negative");
        }
        this.availableProcessors = availableProcessors;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
        this.durationMillis = durationMillis;
        this.suiteVersion = required(suiteVersion, "suiteVersion");
        this.contentVersion = required(contentVersion, "contentVersion");
        this.runtimeBundleHash = required(
                runtimeBundleHash, "runtimeBundleHash");
        this.policyVersion = required(policyVersion, "policyVersion");
        this.modelId = required(modelId, "modelId");
        this.modelDescriptorHash = required(
                modelDescriptorHash, "modelDescriptorHash");
        if (modelDimension < 1) {
            throw new IllegalArgumentException(
                    "modelDimension must be positive");
        }
        this.modelDimension = modelDimension;
    }

    public String getJavaVersion() { return javaVersion; }
    public String getJavaRuntimeName() { return javaRuntimeName; }
    public String getJavaVendor() { return javaVendor; }
    public String getOsName() { return osName; }
    public String getOsVersion() { return osVersion; }
    public String getOsArch() { return osArch; }
    public int getAvailableProcessors() { return availableProcessors; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getDurationMillis() { return durationMillis; }
    public String getSuiteVersion() { return suiteVersion; }
    public String getContentVersion() { return contentVersion; }
    public String getRuntimeBundleHash() { return runtimeBundleHash; }
    public String getPolicyVersion() { return policyVersion; }
    public String getModelId() { return modelId; }
    public String getModelDescriptorHash() { return modelDescriptorHash; }
    public int getModelDimension() { return modelDimension; }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
