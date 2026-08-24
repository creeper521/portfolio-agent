package com.portfolio.agent.infrastructure.model.provider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Immutable, non-secret execution metadata for one configured model entry. */
public final class ModelProviderDescriptor {

    private static final Pattern SELECTION_VERSION_FORMAT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final ModelRef modelRef;
    private final String selectionVersion;
    private final String displayName;
    private final int displayOrder;
    private final URI endpoint;
    private final String modelName;
    private final ModelProviderProtocolProfile protocolProfile;
    private final Set<ModelCapability> capabilities;
    private final int contextWindowBudget;
    private final int outputBudget;
    private final String descriptorFingerprint;

    public ModelProviderDescriptor(
            ModelRef modelRef,
            String selectionVersion,
            String displayName,
            int displayOrder,
            URI endpoint,
            String modelName,
            ModelProviderProtocolProfile protocolProfile,
            Set<ModelCapability> capabilities,
            int maxContextTokens,
            int maxOutputTokens) {
        this.modelRef = Objects.requireNonNull(modelRef, "modelRef");
        this.selectionVersion = requireSelectionVersion(selectionVersion);
        this.displayName = requireText(displayName, "displayName");
        this.displayOrder = displayOrder;
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.modelName = requireText(modelName, "modelName");
        this.protocolProfile = Objects.requireNonNull(protocolProfile, "protocolProfile");
        this.capabilities = copyCapabilities(capabilities);
        if (maxContextTokens < 1) {
            throw new IllegalArgumentException("maxContextTokens must be positive");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > maxContextTokens) {
            throw new IllegalArgumentException("maxOutputTokens is invalid");
        }
        this.contextWindowBudget = maxContextTokens;
        this.outputBudget = maxOutputTokens;
        descriptorFingerprint = fingerprint(
                modelRef.value(), selectionVersion, endpoint.toASCIIString(), modelName,
                protocolProfile.name(), canonicalCapabilities(capabilities),
                Integer.toString(maxContextTokens), Integer.toString(maxOutputTokens));
    }

    public ModelRef getModelRef() { return modelRef; }
    public String getSelectionVersion() { return selectionVersion; }
    public String getDisplayName() { return displayName; }
    public int getDisplayOrder() { return displayOrder; }
    public URI getEndpoint() { return endpoint; }
    public String getModelName() { return modelName; }
    public ModelProviderProtocolProfile getProtocolProfile() { return protocolProfile; }
    public Set<ModelCapability> getCapabilities() { return capabilities; }
    public int getMaxContextTokens() { return contextWindowBudget; }
    public int getMaxOutputTokens() { return outputBudget; }
    public String getDescriptorFingerprint() { return descriptorFingerprint; }

    public ModelCatalogEntry publicEntry() {
        return new ModelCatalogEntry(
                modelRef.value(), displayName, displayOrder,
                selectionVersion, capabilities);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    private static URI requireHttpsEndpoint(URI value) {
        if (value == null
                || value.getScheme() == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must be an HTTPS URI with a host");
        }
        return value;
    }

    private static String requireSelectionVersion(String value) {
        if (value == null
                || value.length() > 128
                || !SELECTION_VERSION_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("selectionVersion format is invalid");
        }
        return value;
    }

    private static Set<ModelCapability> copyCapabilities(Set<ModelCapability> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        for (ModelCapability value : values) {
            if (value == null) {
                throw new IllegalArgumentException("capabilities must not contain null");
            }
        }
        return Set.copyOf(values);
    }

    public static String canonicalCapabilities(Set<ModelCapability> values) {
        return values.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    public static String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
