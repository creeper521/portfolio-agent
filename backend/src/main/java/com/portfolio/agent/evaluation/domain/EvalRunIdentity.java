package com.portfolio.agent.evaluation.domain;

import java.util.Objects;

public final class EvalRunIdentity {

    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    private final String gitCommit;
    private final String datasetVersion;
    private final String datasetHash;
    private final String bundleVersion;
    private final String bundleHash;
    private final String promptHash;
    private final String retrievalPolicyHash;
    private final String embeddingModel;
    private final String embeddingArtifactHash;
    private final String provider;
    private final String model;
    private final String modelParametersHash;
    private final String judgeModel;
    private final String judgeRubricVersion;

    private EvalRunIdentity(
            String gitCommit,
            String datasetVersion,
            String datasetHash,
            String bundleVersion,
            String bundleHash,
            String promptHash,
            String retrievalPolicyHash,
            String embeddingModel,
            String embeddingArtifactHash,
            String provider,
            String model,
            String modelParametersHash,
            String judgeModel,
            String judgeRubricVersion) {
        this.gitCommit = Objects.requireNonNull(gitCommit, "gitCommit");
        this.datasetVersion = Objects.requireNonNull(datasetVersion, "datasetVersion");
        this.datasetHash = Objects.requireNonNull(datasetHash, "datasetHash");
        this.bundleVersion = Objects.requireNonNull(bundleVersion, "bundleVersion");
        this.bundleHash = Objects.requireNonNull(bundleHash, "bundleHash");
        this.promptHash = Objects.requireNonNull(promptHash, "promptHash");
        this.retrievalPolicyHash = Objects.requireNonNull(retrievalPolicyHash, "retrievalPolicyHash");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.embeddingArtifactHash = Objects.requireNonNull(embeddingArtifactHash, "embeddingArtifactHash");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.model = Objects.requireNonNull(model, "model");
        this.modelParametersHash = Objects.requireNonNull(modelParametersHash, "modelParametersHash");
        this.judgeModel = Objects.requireNonNull(judgeModel, "judgeModel");
        this.judgeRubricVersion = Objects.requireNonNull(judgeRubricVersion, "judgeRubricVersion");
    }

    public static EvalRunIdentity create(
            String gitCommit,
            String datasetVersion,
            String datasetHash,
            String bundleVersion,
            String bundleHash,
            String promptHash,
            String retrievalPolicyHash,
            String embeddingModel,
            String embeddingArtifactHash,
            String provider,
            String model,
            String modelParametersHash,
            String judgeModel,
            String judgeRubricVersion) {
        return new EvalRunIdentity(
                gitCommit, datasetVersion, datasetHash, bundleVersion, bundleHash,
                promptHash, retrievalPolicyHash, embeddingModel, embeddingArtifactHash,
                provider, model, modelParametersHash, judgeModel, judgeRubricVersion);
    }

    public String getGitCommit() { return gitCommit; }
    public String getDatasetVersion() { return datasetVersion; }
    public String getDatasetHash() { return datasetHash; }
    public String getBundleVersion() { return bundleVersion; }
    public String getBundleHash() { return bundleHash; }
    public String getPromptHash() { return promptHash; }
    public String getRetrievalPolicyHash() { return retrievalPolicyHash; }
    public String getEmbeddingModel() { return embeddingModel; }
    public String getEmbeddingArtifactHash() { return embeddingArtifactHash; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public String getModelParametersHash() { return modelParametersHash; }
    public String getJudgeModel() { return judgeModel; }
    public String getJudgeRubricVersion() { return judgeRubricVersion; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EvalRunIdentity that)) {
            return false;
        }
        return gitCommit.equals(that.gitCommit)
                && datasetVersion.equals(that.datasetVersion)
                && datasetHash.equals(that.datasetHash)
                && bundleVersion.equals(that.bundleVersion)
                && bundleHash.equals(that.bundleHash)
                && promptHash.equals(that.promptHash)
                && retrievalPolicyHash.equals(that.retrievalPolicyHash)
                && embeddingModel.equals(that.embeddingModel)
                && embeddingArtifactHash.equals(that.embeddingArtifactHash)
                && provider.equals(that.provider)
                && model.equals(that.model)
                && modelParametersHash.equals(that.modelParametersHash)
                && judgeModel.equals(that.judgeModel)
                && judgeRubricVersion.equals(that.judgeRubricVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                gitCommit, datasetVersion, datasetHash, bundleVersion, bundleHash,
                promptHash, retrievalPolicyHash, embeddingModel, embeddingArtifactHash,
                provider, model, modelParametersHash, judgeModel, judgeRubricVersion);
    }
}
