package com.portfolio.agent.portfolio.validation;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class PortfolioSnapshotValidator {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9-]{1,64}");

    public void validate(PortfolioSnapshot snapshot) {
        require(snapshot != null, "snapshot is required");
        require(SUPPORTED_SCHEMA_VERSION.equals(snapshot.getSchemaVersion()),
                "unsupported schemaVersion: " + snapshot.getSchemaVersion());
        require(hasText(snapshot.getContentVersion()), "contentVersion is required");
        require(snapshot.getPublishedAt() != null, "publishedAt is required");
        require(snapshot.getOwner() != null, "owner is required");
        require(hasText(snapshot.getOwner().getRole()), "owner role is required");
        require(hasText(snapshot.getOwner().getSummary()), "owner summary is required");

        List<ProjectProfile> projects = requiredList(snapshot.getProjects(), "projects");
        List<QuestionDefinition> questions = requiredList(snapshot.getQuestions(), "questions");
        List<EvidenceRecord> evidence = requiredList(snapshot.getEvidence(), "evidence");

        Map<String, ProjectProfile> projectsById = uniqueById(projects, ProjectProfile::getId, "project");
        Map<String, ProjectProfile> projectsBySlug = uniqueById(projects, ProjectProfile::getSlug,
                "project slug");
        Map<String, QuestionDefinition> questionsById = uniqueById(questions,
                QuestionDefinition::getId, "question");
        Map<String, EvidenceRecord> evidenceById = uniqueById(evidence, EvidenceRecord::getId,
                "evidence");

        require(!projectsBySlug.isEmpty(), "at least one project is required");

        for (QuestionDefinition question : questions) {
            require(projectsById.containsKey(question.getProjectId()),
                    "question project reference does not exist: " + question.getProjectId());
            require(hasText(question.getCanonicalQuestion()), "canonicalQuestion is required");
            requiredNonBlankList(question.getAliases(), "question aliases");
            require(hasText(question.getSuggestion()), "question suggestion is required");
        }

        for (EvidenceRecord item : evidence) {
            require(hasText(item.getTitle()), "evidence title is required: " + item.getId());
            require(item.getType() != null, "evidence type is required: " + item.getId());
            require(item.getPeriodStart() != null && item.getPeriodEnd() != null,
                    "evidence period is required: " + item.getId());
            require(!item.getPeriodEnd().isBefore(item.getPeriodStart()),
                    "evidence period is invalid: " + item.getId());
            require(item.getPublicStatus() == EvidenceStatus.APPROVED,
                    "evidence must be APPROVED: " + item.getId());
            require(item.getRawContentPublic() != null,
                    "evidence rawContentPublic is required: " + item.getId());
            require(!item.getRawContentPublic(),
                    "evidence raw content must not be public: " + item.getId());
            require(item.getSourceCount() > 0,
                    "evidence sourceCount must be positive: " + item.getId());
            require(hasText(item.getSummary()), "evidence summary is required: " + item.getId());
            requiredNonBlankList(item.getSupportedClaims(), "evidence supportedClaims");
        }

        for (ProjectProfile project : projects) {
            require(hasText(project.getSlug()), "project slug is required");
            require(SLUG_PATTERN.matcher(project.getSlug()).matches(),
                    "project slug format is invalid: " + project.getSlug());
            require(hasText(project.getTitle()), "project title is required: " + project.getId());
            require(hasText(project.getSummary()), "project summary is required: " + project.getId());
            require(hasText(project.getBackground()),
                    "project background is required: " + project.getId());
            requiredNonBlankList(project.getResponsibilities(), "project responsibilities");
            require(hasText(project.getSolution()), "project solution is required: " + project.getId());
            requiredNonBlankList(project.getKeyDecisions(), "project keyDecisions");
            requiredNonBlankList(project.getTechnologies(), "project technologies");
            requiredNonBlankList(project.getVerification(), "project verification");
            require(hasText(project.getOutcome()), "project outcome is required: " + project.getId());
            require(hasText(project.getHandoff()), "project handoff is required: " + project.getId());
            require(project.getStatus() != null, "project status is required: " + project.getId());
            require(project.getContributionType() != null,
                    "project contributionType is required: " + project.getId());

            for (String questionId : requiredNonBlankList(
                    project.getQuestionIds(), "project questionIds")) {
                QuestionDefinition question = questionsById.get(questionId);
                require(question != null,
                        "project question reference does not exist: " + questionId);
                require(project.getId().equals(question.getProjectId()),
                        "question reference belongs to a different project: " + questionId);
            }

            for (String evidenceId : requiredNonBlankList(
                    project.getEvidenceIds(), "project evidenceIds")) {
                require(evidenceById.containsKey(evidenceId),
                        "project evidence reference does not exist: " + evidenceId);
            }
        }
    }

    private static <T> List<T> requiredList(List<T> value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    private static List<String> requiredNonBlankList(List<String> value, String field) {
        require(value != null && !value.isEmpty(), field + " must not be empty");
        for (String item : value) {
            require(hasText(item), field + " must not contain blank values");
        }
        return value;
    }

    private static <T> Map<String, T> uniqueById(
            List<T> values,
            Function<T, String> idExtractor,
            String type
    ) {
        Map<String, T> byId = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (T value : values) {
            require(value != null, type + " item must not be null");
            String id = idExtractor.apply(value);
            require(hasText(id), type + " id is required");
            require(seen.add(id), "duplicate " + type + " id: " + id);
            byId.put(id, value);
        }
        return Map.copyOf(byId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPortfolioSnapshotException(message);
        }
    }
}
