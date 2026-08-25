package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 档主公开资料：作品集所有者允许对外展示的身份信息。
 *
 * <p>仅包含公开审校过的字段（姓名、角色、简介、GitHub、联系邮箱、简历链接）；
 * 私人知识库中的任何信息不得进入此对象。快照校验要求 role 与 summary 必填。
 */
public final class OwnerProfile {

    private final String name;
    private final String role;
    private final String summary;
    private final String githubUrl;
    private final String email;
    private final String resumeUrl;

    @JsonCreator
    public OwnerProfile(
            @JsonProperty("name") String name,
            @JsonProperty("role") String role,
            @JsonProperty("summary") String summary,
            @JsonProperty("githubUrl") String githubUrl,
            @JsonProperty("email") String email,
            @JsonProperty("resumeUrl") String resumeUrl
    ) {
        this.name = name;
        this.role = role;
        this.summary = summary;
        this.githubUrl = githubUrl;
        this.email = email;
        this.resumeUrl = resumeUrl;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getSummary() {
        return summary;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OwnerProfile that)) {
            return false;
        }
        return Objects.equals(name, that.name)
                && Objects.equals(role, that.role)
                && Objects.equals(summary, that.summary)
                && Objects.equals(githubUrl, that.githubUrl)
                && Objects.equals(email, that.email)
                && Objects.equals(resumeUrl, that.resumeUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, role, summary, githubUrl, email, resumeUrl);
    }

    @Override
    public String toString() {
        return "OwnerProfile{" +
                "name='" + name + '\'' +
                ", role='" + role + '\'' +
                ", summary='" + summary + '\'' +
                ", githubUrl='" + githubUrl + '\'' +
                ", email='" + email + '\'' +
                ", resumeUrl='" + resumeUrl + '\'' +
                '}';
    }
}
