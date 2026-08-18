package com.portfolio.agent.turn.capability.general;

import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.runtime.OperationMode;

import java.util.Objects;

/** One call, one strict decode and one semantic validation. No retry or fallback. */
public final class GeneralKnowledgeGenerator {
    private final ConversationProviderAccess providerAccess;
    private final ModelOperationPolicyRegistry operationPolicies;
    private final GeneralKnowledgeModelPort modelPort;
    private final GeneralDraftCodec codec;
    private final GeneralDraftValidator validator;

    public GeneralKnowledgeGenerator(
            ConversationProviderAccess providerAccess,
            ModelOperationPolicyRegistry operationPolicies,
            GeneralKnowledgeModelPort modelPort,
            GeneralDraftCodec codec,
            GeneralDraftValidator validator) {
        this.providerAccess = Objects.requireNonNull(providerAccess, "providerAccess");
        this.operationPolicies = Objects.requireNonNull(operationPolicies, "operationPolicies");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public GeneralSemanticResult generate(GeneralKnowledgeRequest request) {
        Objects.requireNonNull(request, "request");
        if (!providerAccess.isAllowed()
                || operationPolicies.get(ModelOperation.GENERAL_KNOWLEDGE).getMode() != OperationMode.ENABLED
                || request.getDeadline().isExpired()) {
            throw new GeneralKnowledgeUnavailableException("general capability is unavailable");
        }
        try {
            return validator.validate(request, codec.decode(modelPort.generate(request)));
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeneralKnowledgeUnavailableException("general generation failed", exception);
        }
    }
}
