package com.portfolio.agent.portfolio.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

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
