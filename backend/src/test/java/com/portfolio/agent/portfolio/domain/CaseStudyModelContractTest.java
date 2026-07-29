package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseStudyModelContractTest {

    @Test
    void caseStudyIsImmutableAndDefensivelyCopiesCollections() {
        List<String> actions = new ArrayList<>(List.of("保留已存在语言映射"));
        CaseStudy value = new CaseStudy(
                "case-multilingual-upload", "CASE-01", "multilingual-image-preservation",
                CaseType.FEATURE, "多语言图片上传结果保留修复", "连续上传不会覆盖既有语言",
                "后续上传会丢失先前语言映射", actions, List.of("合并持久化与本次上传映射"),
                List.of("先上传德语，再上传法语并查询"), "德语与法语映射同时保留",
                List.of("未公开内部地址"), AchievementStatus.DELIVERED,
                ContributionType.PRIMARY, null,
                List.of("claim-case-multilingual-preserve-existing"),
                List.of("evidence-case-multilingual-implementation-and-regression"),
                List.of("timeline-case-multilingual-delivery"),
                List.of("question-case-multilingual-overview")
        );

        actions.add("mutated");

        assertThat(value.getActions()).containsExactly("保留已存在语言映射");
        assertThat(value.getType()).isEqualTo(CaseType.FEATURE);
        assertThat(value.getProjectId()).isNull();
        assertThat(CaseStudy.class.isRecord()).isFalse();
        assertThat(ClaimSubjectType.valueOf("CASE")).isEqualTo(ClaimSubjectType.CASE);
    }
    @Test
    void schemaFourCaseCopiesCollectionIdsAndSupportsInvestigatedStatus() {
        List<String> collectionIds = new ArrayList<>(List.of("collection-1"));
        CaseStudy value = new CaseStudy(
                "case-1", "CASE-01", "case-one", CaseType.INCIDENT,
                "Case one", "Summary", "Problem", List.of("Action"),
                List.of("Decision"), List.of("Verification"), "Outcome",
                List.of("Limitation"), AchievementStatus.INVESTIGATED,
                ContributionType.COLLABORATIVE, null, collectionIds,
                List.of("claim-1"), List.of("evidence-1"),
                List.of("timeline-1"), List.of("question-1")
        );

        collectionIds.add("collection-2");

        assertThat(value.getCollectionIds()).containsExactly("collection-1");
        assertThatThrownBy(() -> value.getCollectionIds().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(value.getAchievementStatus()).isEqualTo(AchievementStatus.INVESTIGATED);
    }
}
