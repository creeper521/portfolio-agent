package com.portfolio.agent.evaluation.coverage;

import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stable public-subject view of a checked public runtime bundle.
 */
public final class EvalBundleCatalog {

    private final List<PublicSubject> subjects;
    private final Map<String, PublicSubject> caseSubjectsById;

    public EvalBundleCatalog(RuntimeContentSnapshot snapshot) {
        List<PublicSubject> collected = new ArrayList<PublicSubject>();
        Map<String, PublicSubject> caseById = new HashMap<String, PublicSubject>();
        for (ProjectProfile project : snapshot.getProjects()) {
            collected.add(PublicSubject.project(project));
        }
        for (CaseStudy caseStudy : snapshot.getCases()) {
            PublicSubject subject = PublicSubject.caseStudy(caseStudy);
            collected.add(subject);
            caseById.put(caseStudy.getId(), subject);
        }
        collected.sort(Comparator.comparing(PublicSubject::getCanonicalRef));
        this.subjects = List.copyOf(collected);
        this.caseSubjectsById = Map.copyOf(caseById);
    }

    public List<PublicSubject> getSubjects() {
        return subjects;
    }

    public Optional<PublicSubject> findCaseById(String caseId) {
        return Optional.ofNullable(caseSubjectsById.get(caseId));
    }

    public static final class PublicSubject {

        private final ClaimSubjectType type;
        private final String id;
        private final String slug;
        private final String title;
        private final ContributionType contributionType;
        private final AchievementStatus achievementStatus;
        private final boolean primaryProject;
        private final List<String> featuredCaseIds;
        private final int claimCount;
        private final int questionPresetCount;

        private PublicSubject(ClaimSubjectType type, String id, String slug, String title,
                              ContributionType contributionType,
                              AchievementStatus achievementStatus,
                              boolean primaryProject, List<String> featuredCaseIds,
                              int claimCount, int questionPresetCount) {
            this.type = type;
            this.id = id;
            this.slug = slug;
            this.title = title;
            this.contributionType = contributionType;
            this.achievementStatus = achievementStatus;
            this.primaryProject = primaryProject;
            this.featuredCaseIds = List.copyOf(featuredCaseIds);
            this.claimCount = claimCount;
            this.questionPresetCount = questionPresetCount;
        }

        private static PublicSubject project(ProjectProfile project) {
            return new PublicSubject(
                    ClaimSubjectType.PROJECT, project.getId(), project.getSlug(), project.getTitle(),
                    project.getContributionType(), null,
                    project.getDisplayTier()
                            == com.portfolio.agent.portfolio.domain.ProjectDisplayTier.PRIMARY,
                    project.getFeaturedCaseIds(), project.getClaimIds().size(), 0);
        }

        private static PublicSubject caseStudy(CaseStudy caseStudy) {
            return new PublicSubject(
                    ClaimSubjectType.CASE, caseStudy.getId(), caseStudy.getSlug(), caseStudy.getTitle(),
                    caseStudy.getContributionType(), caseStudy.getAchievementStatus(), false,
                    List.of(), caseStudy.getClaimIds().size(),
                    caseStudy.getQuestionPresetIds().size());
        }

        public ClaimSubjectType getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getSlug() {
            return slug;
        }

        public String getTitle() {
            return title;
        }

        public ContributionType getContributionType() {
            return contributionType;
        }

        public AchievementStatus getAchievementStatus() {
            return achievementStatus;
        }

        public boolean isPrimaryProject() {
            return primaryProject;
        }

        public List<String> getFeaturedCaseIds() {
            return featuredCaseIds;
        }

        public int getClaimCount() {
            return claimCount;
        }

        public int getQuestionPresetCount() {
            return questionPresetCount;
        }

        public EvalSubjectRef toSubjectRef() {
            return new EvalSubjectRef(type, slug);
        }

        public String getCanonicalRef() {
            return type.name() + ":" + slug;
        }
    }
}
