package com.portfolio.agent.answer.synthesis.domain;
import java.util.Set;
public final class AllowedRelation {
    private final String relationAlias; private final String generalAlias; private final String portfolioAlias; private final RelationType relationType; private final Set<String> sharedConcepts; private final Set<String> requiredQualifiers;
    public AllowedRelation(String relationAlias, String generalAlias, String portfolioAlias, RelationType relationType, Set<String> sharedConcepts, Set<String> requiredQualifiers) { this.relationAlias=require(relationAlias); this.generalAlias=require(generalAlias); this.portfolioAlias=require(portfolioAlias); this.relationType=java.util.Objects.requireNonNull(relationType); this.sharedConcepts=sharedConcepts==null?Set.of():Set.copyOf(sharedConcepts); this.requiredQualifiers=requiredQualifiers==null?Set.of():Set.copyOf(requiredQualifiers); }
    public String getRelationAlias(){return relationAlias;} public String getGeneralAlias(){return generalAlias;} public String getPortfolioAlias(){return portfolioAlias;} public RelationType getRelationType(){return relationType;} public Set<String> getSharedConcepts(){return sharedConcepts;} public Set<String> getRequiredQualifiers(){return requiredQualifiers;}
    private static String require(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("relation alias required");return v.trim();}
}
