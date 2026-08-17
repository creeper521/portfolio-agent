package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Deliberately narrow fallback that cannot become a second natural-language routing engine. */
public final class MinimalTurnFallback {

    public Resolution resolve(String currentInput, List<SubjectReference> publicSubjects) {
        if (currentInput == null || currentInput.isBlank() || publicSubjects == null) {
            return Resolution.notApplicable();
        }
        String normalizedInput = normalize(currentInput);
        List<SubjectReference> matches = new ArrayList<>();
        for (SubjectReference subject : publicSubjects) {
            if (subject != null && normalize(subject.getSubjectId()).equals(normalizedInput)) {
                matches.add(subject);
            }
        }
        if (matches.size() != 1) {
            return Resolution.notApplicable();
        }
        return Resolution.exactAliasOverview(matches.getFirst());
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
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
