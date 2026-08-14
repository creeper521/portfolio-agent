package com.portfolio.agent.answer.synthesis.service;

import com.portfolio.agent.answer.synthesis.domain.AllowedRelation;

/** Fail-closed validator: an expression may connect approved material but may not rewrite it. */
public final class CrossDomainCompositionValidator {
    public boolean isValid(
            String expression, String generalMaterial, String portfolioMaterial,
            AllowedRelation relation) {
        if (expression == null || expression.isBlank() || generalMaterial == null
                || portfolioMaterial == null || relation == null) {
            return false;
        }
        String canonical = generalMaterial.trim() + " — " + portfolioMaterial.trim()
                + " (" + relation.getRelationType().name() + ")";
        return expression.trim().equals(canonical);
    }
}
