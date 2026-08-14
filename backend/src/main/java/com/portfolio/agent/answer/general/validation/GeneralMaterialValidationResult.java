package com.portfolio.agent.answer.general.validation;

import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;

public final class GeneralMaterialValidationResult {
    private final GeneralAnswerMaterial material;
    private final String failureCode;
    private GeneralMaterialValidationResult(GeneralAnswerMaterial material, String failureCode) { this.material = material; this.failureCode = failureCode; }
    public static GeneralMaterialValidationResult valid(GeneralAnswerMaterial material) { return new GeneralMaterialValidationResult(material, null); }
    public static GeneralMaterialValidationResult invalid(String failureCode) { return new GeneralMaterialValidationResult(null, failureCode); }
    public boolean isValid() { return material != null; }
    public GeneralAnswerMaterial getMaterial() { return material; }
    public String getFailureCode() { return failureCode; }
}
