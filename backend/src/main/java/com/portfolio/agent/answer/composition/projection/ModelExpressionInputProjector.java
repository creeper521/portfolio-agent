package com.portfolio.agent.answer.composition.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.domain.ComparisonAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.RecommendationAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModelExpressionInputProjector {
    public static final String SCHEMA_VERSION = "portfolio-expression-input.v1";
    private static final int MAX_STATEMENTS = 16;
    private static final int MAX_SERIALIZED_CHARACTERS = 12_000;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExpressionInputDocument project(PortfolioAnswerMaterial material,
            PortfolioCompositionContext context) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(context, "context");
        ExpressionIntent intent = context.getExpressionIntent();
        if (!material.getPublicSubjectLabels().equals(intent.getSubjectDisplayLabels())) {
            throw new IllegalArgumentException("intent subject labels do not match material");
        }

        ExpressionAliasRegistry aliases = new ExpressionAliasRegistry();
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("materialKind", material.getMaterialKind().name());
        root.put("intent", intent(intent));
        root.put("shape", shape(material, context));
        root.put("subjects", subjects(material, aliases));
        root.put("statements", statements(material, aliases));
        if (material instanceof ComparisonAnswerMaterial comparison) {
            root.put("dimensions", dimensions(comparison, aliases));
        } else if (material instanceof RecommendationAnswerMaterial recommendation) {
            root.put("candidates", candidates(recommendation, aliases));
        }
        try {
            String json = mapper.writeValueAsString(root);
            int statementLimit = Math.min(MAX_STATEMENTS,
                    context.getExpressionAllowance().getStatementLimit());
            boolean overLimit = aliases.statementCount() > statementLimit
                    || json.length() > MAX_SERIALIZED_CHARACTERS;
            return new ExpressionInputDocument(json, aliases, overLimit);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("expression input serialization failed", exception);
        }
    }

    private static Map<String, Object> intent(ExpressionIntent intent) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("taskKind", intent.getTaskKind().name());
        value.put("focusMode", intent.getFocusMode().name());
        value.put("requestedFacets", names(intent.getRequestedFacets()));
        value.put("requestedDimensions", names(intent.getRequestedDimensions()));
        value.put("requestedOutputs", names(intent.getRequestedOutputs()));
        value.put("audienceRole", intent.getAudienceRole().name());
        value.put("responseDepth", intent.getResponseDepth().name());
        value.put("locale", intent.getLocale().toString());
        value.put("taskSource", intent.getTaskSource().name());
        return value;
    }

    private static Map<String, Object> shape(PortfolioAnswerMaterial material,
            PortfolioCompositionContext context) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        if (material instanceof FactAnswerMaterial fact) {
            value.put("summaryPolicy", fact.getSummaryPolicy().name());
            value.put("allowedSections", fact.getSections().stream()
                    .map(section -> section.getSectionType().name()).toList());
            value.put("requiredSections", fact.getSections().stream()
                    .filter(section -> section.getStatementEntries().stream().anyMatch(entry ->
                            entry.getPresentationRole()
                                    == com.portfolio.agent.answer.composition.domain.PresentationRole.REQUIRED))
                    .map(section -> section.getSectionType().name()).toList());
        }
        value.put("maxCharacters", Math.min(2400,
                context.getExpressionAllowance().getCharacterLimit()));
        value.put("fixedBoundaryPresent", !material.getFixedCaveats().isEmpty()
                || !material.getOmittedTopicLabels().isEmpty());
        return value;
    }

    private static List<Map<String, Object>> subjects(PortfolioAnswerMaterial material,
            ExpressionAliasRegistry aliases) {
        List<Map<String, Object>> values = new ArrayList<>();
        List<SubjectReference> subjects;
        if (material instanceof FactAnswerMaterial fact) subjects = List.of(fact.getSubject());
        else if (material instanceof ComparisonAnswerMaterial comparison) subjects = comparison.getOrderedSubjects();
        else subjects = List.of();
        for (int index = 0; index < subjects.size(); index++) {
            String alias = String.format("P%02d", index + 1);
            SubjectReference subject = subjects.get(index);
            aliases.addSubject(alias, subject);
            values.add(Map.of("key", alias, "label", subject.getPublicLabel()));
        }
        return values;
    }

    private static List<Map<String, Object>> statements(PortfolioAnswerMaterial material,
            ExpressionAliasRegistry aliases) {
        List<Map<String, Object>> values = new ArrayList<>();
        List<ExpressionStatement> entries = material.getExpressionStatements();
        for (int index = 0; index < entries.size(); index++) {
            ExpressionStatement entry = entries.get(index);
            String alias = String.format("S%03d", index + 1);
            aliases.addStatement(alias, entry);
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("key", alias);
            value.put("role", entry.getPresentationRole().name());
            value.put("section", entry.getAllowedSection().name());
            SubjectReference subject = entry.getStatement().getSubjectReferences().get(0);
            String subjectAlias = aliases.aliasOf(subject);
            if (subjectAlias != null) value.put("subjectKey", subjectAlias);
            value.put("predicate", entry.getStatement().getControlledPredicate().name());
            value.put("statement", entry.getStatement().getPublicStatement());
            value.put("detail", entry.getStatement().getPublicDetail());
            value.put("claimCategory", entry.getStatement().getClaimCategory().name());
            value.put("achievementStatus", entry.getStatement().getAchievementStatus().name());
            value.put("contributionType", entry.getStatement().getContributionType().name());
            value.put("verificationBasis", entry.getStatement().getVerificationBasis().name());
            value.put("materiality", entry.getStatement().getMateriality().name());
            value.put("supportTarget", entry.getStatement().getSupportTarget().name());
            values.add(value);
        }
        return values;
    }

    private static List<Map<String, Object>> dimensions(ComparisonAnswerMaterial material,
            ExpressionAliasRegistry aliases) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < material.getOrderedDimensions().size(); index++) {
            String alias = String.format("D%02d", index + 1);
            aliases.addDimension(alias, material.getOrderedDimensions().get(index).getDimensionKey());
            values.add(Map.of("key", alias));
        }
        return values;
    }

    private static List<Map<String, Object>> candidates(RecommendationAnswerMaterial material,
            ExpressionAliasRegistry aliases) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < material.getOrderedCandidates().size(); index++) {
            String alias = String.format("C%02d", index + 1);
            RecommendationAnswerMaterial.RecommendationCandidate candidate =
                    material.getOrderedCandidates().get(index);
            aliases.addCandidate(alias, candidate.getCandidateReference());
            values.add(Map.of("key", alias, "label", candidate.getCandidateReference().getPublicLabel()));
        }
        return values;
    }

    private static List<String> names(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toList();
    }
}
