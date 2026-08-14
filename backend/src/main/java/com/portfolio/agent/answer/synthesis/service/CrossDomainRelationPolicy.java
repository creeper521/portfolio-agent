package com.portfolio.agent.answer.synthesis.service;
import com.portfolio.agent.answer.synthesis.domain.AllowedRelation;
import com.portfolio.agent.answer.synthesis.domain.RelationType;
import java.util.List; import java.util.Set;
public final class CrossDomainRelationPolicy {
    public List<AllowedRelation> allow(String generalAlias,String portfolioAlias,Set<String> sharedConcepts){
        if(generalAlias==null||portfolioAlias==null||sharedConcepts==null||sharedConcepts.isEmpty())return List.of();
        return List.of(new AllowedRelation("relation-1",generalAlias,portfolioAlias,RelationType.ILLUSTRATES,sharedConcepts,Set.of()));
    }
    public List<AllowedRelation> allow(
            String generalAlias, String portfolioAlias, String generalMaterial,
            String portfolioMaterial, Set<String> candidateConcepts) {
        if (generalMaterial == null || portfolioMaterial == null || candidateConcepts == null) return List.of();
        String general = normalize(generalMaterial);
        String portfolio = normalize(portfolioMaterial);
        Set<String> shared = candidateConcepts.stream().filter(java.util.Objects::nonNull)
                .map(CrossDomainRelationPolicy::normalize)
                .filter(value -> !value.isBlank() && general.contains(value) && portfolio.contains(value))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return allow(generalAlias, portfolioAlias, shared);
    }
    private static String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').trim();
    }
}
