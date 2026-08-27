package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputStrategy;
import com.portfolio.agent.infrastructure.model.structured.TokenFieldPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedModelExecutionProfileTest {

    @Test
    void qwenV5FreezesProviderDraftCompilersAndOmittedTokenField() {
        ApprovedModelExecutionProfile profile = ApprovedModelExecutionProfile.resolve(
                ApprovedModelExecutionProfile.QWEN_PROFILE,
                StructuredModelTestFixtures.contracts());

        assertThat(profile.getRequiredSelectionVersion())
                .isEqualTo("qwen-3-7-flash-v6");
        assertThat(profile.getExpectedModelIdentity()).isEqualTo("qwen3.7-flash");
        assertThat(profile.getProtocolProfile()).isEqualTo(
                ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS);
        assertThat(profile.getOperationBindings().values())
                .allSatisfy(binding -> {
                    assertThat(binding.getStrategy()).isEqualTo(
                            StructuredOutputStrategy.REQUIRED_TOOL_CALL);
                    assertThat(binding.getTokenFieldPolicy()).isEqualTo(
                            TokenFieldPolicy.OMIT);
                });
        OperationBinding goal = profile.getOperationBindings()
                .get(ModelOperation.TURN_INTERPRETATION);
        assertThat(goal.getProviderContractRef().schemaVersion())
                .isEqualTo("goal.provider-draft.v2");
        assertThat(goal.getApplicationContractRef().schemaVersion())
                .isEqualTo("goal.proposal.v5");
        assertThat(goal.getOutputCompilerProfileVersion())
                .isEqualTo(OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION);
        assertThat(goal.outputToolName()).isEqualTo("emit_goal_provider_draft_v2");
        assertThat(goal.getProviderContractFingerprint())
                .isNotEqualTo(goal.getApplicationContractFingerprint());
        OperationBinding general = profile.getOperationBindings()
                .get(ModelOperation.GENERAL_KNOWLEDGE);
        assertThat(general.getProviderContractRef().schemaVersion())
                .isEqualTo("general.provider-draft.v3");
        assertThat(general.getApplicationContractRef().schemaVersion())
                .isEqualTo("general.draft.v2");
        assertThat(general.getOutputCompilerProfileVersion())
                .isEqualTo(OperationBinding.GENERAL_DRAFT_OUTPUT_COMPILER_VERSION);
        assertThat(general.outputToolName()).isEqualTo("emit_general_provider_draft_v3");
    }

    @Test
    void glmProfileFreezesRequiredToolAndMaxTokensForBothOperations() {
        ApprovedModelExecutionProfile profile = ApprovedModelExecutionProfile.resolve(
                ApprovedModelExecutionProfile.GLM_PROFILE,
                StructuredModelTestFixtures.contracts());

        assertThat(profile.getRequiredSelectionVersion())
                .isEqualTo("glm-4-7-flash-v4");
        assertThat(profile.getExpectedModelIdentity()).isEqualTo("glm-4.7-flash");
        assertThat(profile.getOperationBindings()).containsOnlyKeys(
                ModelOperation.TURN_INTERPRETATION,
                ModelOperation.GENERAL_KNOWLEDGE);
        assertThat(profile.getOperationBindings().values())
                .extracting(OperationBinding::getStrategy)
                .containsOnly(StructuredOutputStrategy.REQUIRED_TOOL_CALL);
        OperationBinding goal = profile.getOperationBindings()
                .get(ModelOperation.TURN_INTERPRETATION);
        assertThat(goal.getProviderContractRef().schemaVersion())
                .isEqualTo("goal.provider-draft.v2");
        assertThat(goal.outputToolName()).isEqualTo("emit_goal_provider_draft_v2");
        OperationBinding general = profile.getOperationBindings()
                .get(ModelOperation.GENERAL_KNOWLEDGE);
        assertThat(general.getProviderContractRef().schemaVersion())
                .isEqualTo("general.draft.v2");
        assertThat(general.getApplicationContractRef().schemaVersion())
                .isEqualTo("general.draft.v2");
        assertThat(general.getOutputCompilerProfileVersion())
                .isEqualTo(OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION);
    }

    @Test
    void unknownOrMismatchedProfileFailsClosedWithoutPartialAdmission() {
        assertThatThrownBy(() -> ApprovedModelExecutionProfile.resolve(
                "UNAPPROVED", StructuredModelTestFixtures.contracts()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not approved");

        ApprovedModelExecutionProfile profile = ApprovedModelExecutionProfile.resolve(
                ApprovedModelExecutionProfile.QWEN_PROFILE,
                StructuredModelTestFixtures.contracts());
        assertThatThrownBy(() -> profile.requireMatches(
                "qwen-3-7-flash-v1", "qwen3.7-flash",
                ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selectionVersion");
    }

    @Test
    void dualContractFingerprintsAndCompilerAuthorityChangeBindingIdentity() {
        ApprovedModelExecutionProfile profile = ApprovedModelExecutionProfile.resolve(
                ApprovedModelExecutionProfile.QWEN_PROFILE,
                StructuredModelTestFixtures.contracts());
        OperationBinding original = profile.getOperationBindings()
                .get(ModelOperation.TURN_INTERPRETATION);
        OperationBinding changedProviderFingerprint = new OperationBinding(
                original.getOperation(), original.getProviderContractRef(),
                "1".repeat(64), original.getApplicationContractRef(),
                original.getApplicationContractFingerprint(),
                "goal_provider_draft",
                OperationBinding.GOAL_DRAFT_OUTPUT_COMPILER_VERSION,
                original.getStrategy(), original.getTokenFieldPolicy(),
                original.getRequestCompilerProfileVersion(),
                original.getResponseExtractorProfileVersion());

        assertThat(changedProviderFingerprint.getBindingFingerprint())
                .isNotEqualTo(original.getBindingFingerprint());
        assertThatThrownBy(() -> new OperationBinding(
                original.getOperation(), original.getProviderContractRef(),
                original.getProviderContractFingerprint(),
                original.getApplicationContractRef(),
                original.getApplicationContractFingerprint(),
                "goal_provider_draft",
                OperationBinding.IDENTITY_OUTPUT_COMPILER_VERSION,
                original.getStrategy(), original.getTokenFieldPolicy(),
                original.getRequestCompilerProfileVersion(),
                original.getResponseExtractorProfileVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compiler");
    }
}
