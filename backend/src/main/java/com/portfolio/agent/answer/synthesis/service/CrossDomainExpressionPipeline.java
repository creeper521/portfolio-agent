package com.portfolio.agent.answer.synthesis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.runtime.OperationMode;
import com.portfolio.agent.answer.synthesis.codec.CrossDomainDraftCodec;
import com.portfolio.agent.answer.synthesis.codec.CrossDomainDraftException;
import com.portfolio.agent.answer.synthesis.domain.AllowedRelation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Optional expression pass over approved deterministic material; never a source of relations. */
public final class CrossDomainExpressionPipeline {
    private final ConversationalModelPort modelPort;
    private final ModelOperationPolicyRegistry operationPolicies;
    private final CrossDomainDraftCodec codec;
    private final ObjectMapper objectMapper;
    private final CrossDomainCompositionValidator compositionValidator;

    public CrossDomainExpressionPipeline(
            ConversationalModelPort modelPort,
            ModelOperationPolicyRegistry operationPolicies) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.operationPolicies = Objects.requireNonNull(operationPolicies, "operationPolicies");
        this.codec = new CrossDomainDraftCodec();
        this.objectMapper = new ObjectMapper();
        this.compositionValidator = new CrossDomainCompositionValidator();
    }

    public Optional<String> express(
            String generalText, String portfolioText, AllowedRelation relation) {
        if (operationPolicies.get(ModelOperation.CROSS_DOMAIN_EXPRESSION).getMode()
                != OperationMode.ENABLED) {
            return Optional.empty();
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("generalMaterial", generalText);
        input.put("portfolioMaterial", portfolioText);
        input.put("allowedRelation", relation.getRelationAlias());
        input.put("relationType", relation.getRelationType().name());
        input.put("statementAliases", java.util.List.of(
                relation.getGeneralAlias(), relation.getPortfolioAlias()));
        input.put("requiredQualifiers", relation.getRequiredQualifiers());
        try {
            ConversationModelResult<String> response = modelPort.generateCrossDomainExpression(
                    objectMapper.writeValueAsString(input));
            if (response == null || !response.isSuccessful() || response.getValue() == null) {
                return Optional.empty();
            }
            CrossDomainDraftCodec.Draft draft = codec.decode(response.getValue());
            if (!relation.getRelationAlias().equals(draft.getRelationAlias())
                    || !draft.getStatementAliases().contains(relation.getGeneralAlias())
                    || !draft.getStatementAliases().contains(relation.getPortfolioAlias())
                    || !draft.getCaveatAliases().containsAll(relation.getRequiredQualifiers())
                    || !compositionValidator.isValid(
                            draft.getText(), generalText, portfolioText, relation)) {
                return Optional.empty();
            }
            return Optional.of(draft.getText());
        } catch (JsonProcessingException | CrossDomainDraftException exception) {
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
