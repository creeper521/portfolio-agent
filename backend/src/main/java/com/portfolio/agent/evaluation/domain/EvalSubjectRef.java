package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;

public final class EvalSubjectRef {

    private final ClaimSubjectType type;
    private final String slug;

    @JsonCreator
    public EvalSubjectRef(@JsonProperty("type") ClaimSubjectType type,
                          @JsonProperty("slug") String slug) {
        this.type = type;
        this.slug = slug;
    }

    public ClaimSubjectType getType() {
        return type;
    }

    public String getSlug() {
        return slug;
    }
}
