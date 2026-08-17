package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deliberately narrow fallback that cannot become a second natural-language routing engine. */
public final class MinimalTurnFallback {

    private final ReferenceMatchPolicy referenceMatchPolicy = new ReferenceMatchPolicy();

    public Resolution resolve(String currentInput, List<SubjectReference> publicSubjects) {
        if (currentInput == null || currentInput.isBlank() || publicSubjects == null) {
            return Resolution.notApplicable();
        }
        List<SubjectReference> matches = new ArrayList<>();
        for (SubjectReference subject : publicSubjects) {
            if (subject != null && referenceMatchPolicy.matches(
                    new com.portfolio.agent.answer.routing.domain.TextAnchor(currentInput.trim(), 1),
                    currentInput, subject.getSubjectId())) {
                matches.add(subject);
            }
        }
        if (matches.size() != 1) {
            return Resolution.notApplicable();
        }
        return Resolution.exactAliasOverview(matches.getFirst());
    }

    public enum Disposition { EXACT_ALIAS_OVERVIEW, NOT_APPLICABLE }

    public static final class Resolution {

        private final Disposition disposition;
        private final SubjectReference subject;

        private Resolution(Disposition disposition, SubjectReference subject) {
            this.disposition = Objects.requireNonNull(disposition, "disposition");
            this.subject = subject;
            if ((disposition == Disposition.EXACT_ALIAS_OVERVIEW) != (subject != null)) {
                throw new IllegalArgumentException("fallback resolution fields are incompatible");
            }
        }

        public static Resolution exactAliasOverview(SubjectReference subject) {
            return new Resolution(Disposition.EXACT_ALIAS_OVERVIEW, Objects.requireNonNull(subject, "subject"));
        }

        public static Resolution notApplicable() {
            return new Resolution(Disposition.NOT_APPLICABLE, null);
        }

        public Disposition getDisposition() { return disposition; }
        public Optional<SubjectReference> getSubject() { return Optional.ofNullable(subject); }
    }
}
