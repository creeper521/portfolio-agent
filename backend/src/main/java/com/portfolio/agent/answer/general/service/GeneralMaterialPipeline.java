package com.portfolio.agent.answer.general.service;

import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;
import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterialDraft;
import com.portfolio.agent.answer.general.domain.GeneralStatementRole;
import com.portfolio.agent.answer.general.domain.GeneralSupportKind;
import com.portfolio.agent.answer.general.render.DeterministicGeneralRenderer;
import com.portfolio.agent.answer.general.validation.GeneralMaterialValidationResult;
import com.portfolio.agent.answer.general.validation.GeneralMaterialValidator;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.general.codec.GeneralAnswerMaterialDraftCodec;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Typed General Material pipeline; it never consumes the legacy ConversationDraft. */
public final class GeneralMaterialPipeline {
    private final ConversationProviderAccess providerAccess;
    private final ConversationalModelPort modelPort;
    private final GeneralMaterialValidator validator;
    private final DeterministicGeneralRenderer renderer;
    private final GeneralAnswerMaterialDraftCodec codec;
    private final ModelOperationPolicyRegistry operationPolicies;

    public GeneralMaterialPipeline(
            ConversationProviderAccess providerAccess,
            ConversationalModelPort modelPort,
            ModelOperationPolicyRegistry operationPolicies) {
        this.providerAccess = java.util.Objects.requireNonNull(providerAccess, "providerAccess");
        this.modelPort = java.util.Objects.requireNonNull(modelPort, "modelPort");
        this.validator = new GeneralMaterialValidator();
        this.renderer = new DeterministicGeneralRenderer();
        this.codec = new GeneralAnswerMaterialDraftCodec();
        this.operationPolicies = java.util.Objects.requireNonNull(operationPolicies, "operationPolicies");
    }

    public Result generate(
            String question,
            ConversationWindow window,
            ConversationRoute route,
            String expectedContentVersion,
            String audienceRole) {
        if (!providerAccess.isAllowed()) return Result.unavailable();
        if (operationPolicies.get(ModelOperation.GENERAL_ANSWER_MATERIAL).getMode()
                != com.portfolio.agent.answer.runtime.OperationMode.ENABLED) {
            return Result.unavailable();
        }
        com.portfolio.agent.answer.domain.ConversationModelResult<String> generated =
                modelPort.generateGeneralMaterial(
                        question, window, route, expectedContentVersion, audienceRole);
        if (generated == null || !generated.isSuccessful() || generated.getValue() == null) return Result.unavailable();
        GeneralAnswerMaterialDraft draft;
        try {
            draft = codec.decode(generated.getValue());
        } catch (RuntimeException exception) {
            return Result.rejected("GENERAL_DRAFT_REJECTED");
        }
        if (!expectedContentVersion.equals(draft.getMetadata().getContentVersion())
                || !audienceRole.equals(draft.getMetadata().getAudienceRole())) {
            return Result.rejected("GENERAL_METADATA_MISMATCH");
        }
        GeneralMaterialValidationResult validation = validator.validate(draft);
        if (!validation.isValid()) return Result.rejected(validation.getFailureCode());
        return Result.success(validation.getMaterial(), renderer.render(validation.getMaterial()));
    }

    public static final class Result {
        private final GeneralAnswerMaterial material;
        private final TaskResultPayload.SectionResultPayload payload;
        private final String failureCode;
        private Result(GeneralAnswerMaterial material, TaskResultPayload.SectionResultPayload payload, String failureCode) {
            this.material = material; this.payload = payload; this.failureCode = failureCode;
        }
        public static Result success(GeneralAnswerMaterial material, TaskResultPayload.SectionResultPayload payload) { return new Result(material, payload, null); }
        public static Result unavailable() { return new Result(null, null, "GENERAL_PROVIDER_UNAVAILABLE"); }
        public static Result rejected(String failureCode) { return new Result(null, null, failureCode); }
        public boolean isSuccessful() { return material != null && payload != null; }
        public GeneralAnswerMaterial getMaterial() { return material; }
        public TaskResultPayload.SectionResultPayload getPayload() { return payload; }
        public String getFailureCode() { return failureCode; }
    }
}
