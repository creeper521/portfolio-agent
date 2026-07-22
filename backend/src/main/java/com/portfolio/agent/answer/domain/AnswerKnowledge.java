package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerKnowledge {

    private final String slug;
    private final String title;
    private final String summary;
    private final String background;
    private final List<String> responsibilities;
    private final String solution;
    private final List<String> keyDecisions;
    private final List<String> verification;
    private final String outcome;
    private final String handoff;
    private final String status;
    private final List<AnswerQuestion> questions;
    private final List<AnswerEvidence> evidence;
    private final List<AnswerClaimProjection> claims;

    public AnswerKnowledge(
            String slug,
            String title,
            String summary,
            String background,
            List<String> responsibilities,
            String solution,
            List<String> keyDecisions,
            List<String> verification,
            String outcome,
            String handoff,
            String status,
            List<AnswerQuestion> questions,
            List<AnswerEvidence> evidence,
            List<AnswerClaimProjection> claims
    ) {
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.background = background;
        this.responsibilities = List.copyOf(responsibilities);
        this.solution = solution;
        this.keyDecisions = List.copyOf(keyDecisions);
        this.verification = List.copyOf(verification);
        this.outcome = outcome;
        this.handoff = handoff;
        this.status = status;
        this.claims = List.copyOf(claims);
        this.questions = List.copyOf(questions);
        this.evidence = List.copyOf(evidence);
    }

    public AnswerKnowledge(String slug, String title, String summary, String background,
            List<String> responsibilities, String solution, List<String> keyDecisions,
            List<String> verification, String outcome, String handoff, String status,
            List<AnswerQuestion> questions, List<AnswerEvidence> evidence) {
        this(slug, title, summary, background, responsibilities, solution, keyDecisions,
                verification, outcome, handoff, status, questions, evidence, List.of());
    }

    public AnswerKnowledge(
            String slug,
            String title,
            String background,
            List<String> responsibilities,
            String solution,
            List<String> keyDecisions,
            List<String> verification,
            String outcome,
            String handoff,
            String status,
            List<AnswerQuestion> questions,
            List<AnswerEvidence> evidence
    ) {
        this(slug, title, title, background, responsibilities, solution, keyDecisions,
                verification, outcome, handoff, status, questions, evidence, List.of());
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() { return summary; }

    public String getBackground() {
        return background;
    }

    public List<String> getResponsibilities() {
        return responsibilities;
    }

    public String getSolution() {
        return solution;
    }

    public List<String> getKeyDecisions() {
        return keyDecisions;
    }

    public List<String> getVerification() {
        return verification;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getHandoff() {
        return handoff;
    }

    public String getStatus() {
        return status;
    }

    public List<AnswerQuestion> getQuestions() {
        return questions;
    }

    public List<AnswerEvidence> getEvidence() {
        return evidence;
    }

    public List<AnswerClaimProjection> getClaims() { return claims; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerKnowledge that)) {
            return false;
        }
        return Objects.equals(slug, that.slug)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary)
                && Objects.equals(background, that.background)
                && Objects.equals(responsibilities, that.responsibilities)
                && Objects.equals(solution, that.solution)
                && Objects.equals(keyDecisions, that.keyDecisions)
                && Objects.equals(verification, that.verification)
                && Objects.equals(outcome, that.outcome)
                && Objects.equals(handoff, that.handoff)
                && Objects.equals(status, that.status)
                && Objects.equals(questions, that.questions)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(claims, that.claims);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, title, summary, background, responsibilities, solution, keyDecisions,
                verification, outcome, handoff, status, questions, evidence, claims);
    }

    @Override
    public String toString() {
        return "AnswerKnowledge{" +
                "slug='" + slug + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", background='" + background + '\'' +
                ", responsibilities=" + responsibilities +
                ", solution='" + solution + '\'' +
                ", keyDecisions=" + keyDecisions +
                ", verification=" + verification +
                ", outcome='" + outcome + '\'' +
                ", handoff='" + handoff + '\'' +
                ", status='" + status + '\'' +
                ", questions=" + questions +
                ", evidence=" + evidence +
                ", claims=" + claims +
                '}';
    }
}
