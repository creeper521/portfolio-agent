package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimModelContractTest {

    @Test
    void claimKeepsOrthogonalIdentityLifecycleBasisVerificationAndMateriality() {
        List<String> topics = new ArrayList<>(List.of("ASYNC_TASK"));
        Map<String, Integer> priorities = new LinkedHashMap<>();
        priorities.put("INTERVIEWER", 100);

        Claim claim = new Claim(
                "claim-sql-audit-delivered",
                ClaimSubjectType.PROJECT,
                "sql-audit-project",
                ClaimCategory.OUTCOME,
                "核心版本已完成部署并形成使用文档。",
                "只表达审核后的交付边界。",
                AchievementStatus.DELIVERED,
                ContributionType.PRIMARY,
                VerificationBasis.EVIDENCE_SUPPORTED,
                ClaimVerificationStatus.VERIFIED,
                Materiality.KEY,
                topics,
                priorities
        );

        topics.add("PRIVATE_TOPIC");
        priorities.put("GUEST", 10);

        assertThat(claim.getSubjectType()).isEqualTo(ClaimSubjectType.PROJECT);
        assertThat(claim.getSubjectId()).isEqualTo("sql-audit-project");
        assertThat(claim.getCategory()).isEqualTo(ClaimCategory.OUTCOME);
        assertThat(claim.getAchievementStatus()).isEqualTo(AchievementStatus.DELIVERED);
        assertThat(claim.getVerificationBasis()).isEqualTo(VerificationBasis.EVIDENCE_SUPPORTED);
        assertThat(claim.getVerificationStatus()).isEqualTo(ClaimVerificationStatus.VERIFIED);
        assertThat(claim.getMateriality()).isEqualTo(Materiality.KEY);
        assertThat(claim.getTopics()).containsExactly("ASYNC_TASK");
        assertThat(claim.getAudiencePriorities()).containsOnlyKeys("INTERVIEWER");
        assertThatThrownBy(() -> claim.getTopics().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> claim.getAudiencePriorities().put("GUEST", 10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void claimEvidenceLinkIsTheOnlyClaimEvidenceSupportRelationship()
            throws IntrospectionException {
        ClaimEvidenceLink link = new ClaimEvidenceLink(
                "link-delivery-e01",
                "claim-sql-audit-delivered",
                "sql-audit-delivery-set",
                SupportType.DIRECT,
                "证明核心版本已经交付；不证明长期生产效果。",
                ReviewStatus.APPROVED
        );

        assertThat(link.getClaimId()).isEqualTo("claim-sql-audit-delivered");
        assertThat(link.getEvidenceId()).isEqualTo("sql-audit-delivery-set");
        assertThat(link.getSupportType()).isEqualTo(SupportType.DIRECT);

        assertThat(propertyNames(EvidenceRecord.class)).doesNotContain(
                "supportedClaims", "claimIds", "supportedClaimIds"
        );
        assertThat(propertyNames(Claim.class)).doesNotContain(
                "type", "claimType", "evidenceIds"
        );
    }

    @Test
    void schemaTwoUsesExplicitProjectQuestionAndTimelineReferences()
            throws IntrospectionException {
        assertThat(propertyNames(ProjectProfile.class))
                .contains("claimIds", "evidenceIds", "timelineEventIds")
                .doesNotContain("questionIds");
        assertThat(propertyNames(QuestionDefinition.class))
                .contains("text", "projectIds", "preferredClaimCategories",
                        "deterministicEntry", "displayOrder")
                .doesNotContain("projectId", "canonicalQuestion", "suggestion");
        assertThat(propertyNames(TimelineEvent.class))
                .contains("projectIds", "claimIds", "evidenceIds")
                .doesNotContain("projectSlugs");
    }

    private List<String> propertyNames(Class<?> type) throws IntrospectionException {
        return List.of(Introspector.getBeanInfo(type).getPropertyDescriptors()).stream()
                .map(PropertyDescriptor::getName)
                .toList();
    }
}
