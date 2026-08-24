package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.util.Optional;
import java.util.Set;

/**
 * Immutable request-time model authority shared by every model stage in one Turn.
 * It is credential-free and safe to use as public projection input.
 */
public final class ModelExecutionSnapshot {
    private static final ModelExecutionSnapshot NONE = new ModelExecutionSnapshot();

    private final Kind kind;
    private final ModelRef modelRef;
    private final String selectionVersion;
    private final String descriptorFingerprint;
    private final ModelProviderProtocolProfile protocolProfile;
    private final Set<ModelCapability> capabilities;
    private final int maxContextTokens;
    private final int maxOutputTokens;

    private ModelExecutionSnapshot() {
        kind = Kind.NONE;
        modelRef = null;
        selectionVersion = null;
        descriptorFingerprint = null;
        protocolProfile = null;
        capabilities = Set.of();
        maxContextTokens = 0;
        maxOutputTokens = 0;
    }

    private ModelExecutionSnapshot(ModelProviderDescriptor descriptor) {
        ModelProviderDescriptor required = java.util.Objects.requireNonNull(
                descriptor, "descriptor");
        kind = Kind.MODEL;
        modelRef = required.getModelRef();
        selectionVersion = required.getSelectionVersion();
        descriptorFingerprint = required.getDescriptorFingerprint();
        protocolProfile = required.getProtocolProfile();
        capabilities = Set.copyOf(required.getCapabilities());
        maxContextTokens = required.getMaxContextTokens();
        maxOutputTokens = required.getMaxOutputTokens();
    }

    public static ModelExecutionSnapshot none() {
        return NONE;
    }

    public static ModelExecutionSnapshot model(ModelProviderDescriptor descriptor) {
        return new ModelExecutionSnapshot(descriptor);
    }

    public Kind getKind() {
        return kind;
    }

    public Optional<ModelRef> getModelRef() {
        return Optional.ofNullable(modelRef);
    }

    public Optional<String> getSelectionVersion() {
        return Optional.ofNullable(selectionVersion);
    }

    public Optional<String> getDescriptorFingerprint() {
        return Optional.ofNullable(descriptorFingerprint);
    }

    public Optional<ModelProviderProtocolProfile> getProtocolProfile() {
        return Optional.ofNullable(protocolProfile);
    }

    public Set<ModelCapability> getCapabilities() {
        return capabilities;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public boolean supports(ModelCapability capability) {
        return capabilities.contains(java.util.Objects.requireNonNull(
                capability, "capability"));
    }

    @Override
    public String toString() {
        return kind == Kind.NONE
                ? "ModelExecutionSnapshot{kind=NONE}"
                : "ModelExecutionSnapshot{kind=MODEL, modelRef=" + modelRef
                + ", selectionVersion=" + selectionVersion
                + ", descriptorFingerprint=" + descriptorFingerprint + '}';
    }

    public enum Kind {
        MODEL,
        NONE
    }
}
