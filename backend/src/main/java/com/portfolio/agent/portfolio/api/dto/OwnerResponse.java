package com.portfolio.agent.portfolio.api.dto;

import com.portfolio.agent.portfolio.domain.model.OwnerProfile;

import java.util.Objects;

public final class OwnerResponse {

    private final String name;
    private final String role;
    private final String summary;
    private final String githubUrl;
    private final String email;
    private final String resumeUrl;

    public OwnerResponse(
            String name,
            String role,
            String summary,
            String githubUrl,
            String email,
            String resumeUrl
    ) {
        this.name = name;
        this.role = role;
        this.summary = summary;
        this.githubUrl = githubUrl;
        this.email = email;
        this.resumeUrl = resumeUrl;
    }

    public static OwnerResponse from(OwnerProfile owner) {
        return new OwnerResponse(
                owner.getName(),
                owner.getRole(),
                owner.getSummary(),
                owner.getGithubUrl(),
                owner.getEmail(),
                owner.getResumeUrl()
        );
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
        if (!(other instanceof OwnerResponse that)) {
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
        return "OwnerResponse{" +
                "name='" + name + '\'' +
                ", role='" + role + '\'' +
                ", summary='" + summary + '\'' +
                ", githubUrl='" + githubUrl + '\'' +
                ", email='" + email + '\'' +
                ", resumeUrl='" + resumeUrl + '\'' +
                '}';
    }
}
