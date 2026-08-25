package com.portfolio.agent.turn.capability.portfolio.evidence;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;

import java.util.List;
import java.util.Objects;

/**
 * 晋级通过的已验证 Evidence 捆绑包（不可变值对象）。
 *
 * <p>由 {@link EvidencePromotionValidator} 整批产出，包含获准主体范围、内容发布 ID
 * 与去重后的 Evidence 单元列表；构造期要求发布 ID 与主体范围一致。
 */
public final class ValidatedEvidenceBundle {
    private final AuthorizedSubjectScope scope;
    private final String contentReleaseId;
    private final List<ValidatedEvidenceUnit> units;
    public ValidatedEvidenceBundle(
            AuthorizedSubjectScope scope, String contentReleaseId,
            List<ValidatedEvidenceUnit> units) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.contentReleaseId = Objects.requireNonNull(contentReleaseId, "contentReleaseId");
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
        if (!contentReleaseId.equals(scope.getContentReleaseId())) {
            throw new IllegalArgumentException("bundle release mismatch");
        }
    }
    public AuthorizedSubjectScope getScope() { return scope; }
    public String getContentReleaseId() { return contentReleaseId; }
    public List<ValidatedEvidenceUnit> getUnits() { return units; }
}
