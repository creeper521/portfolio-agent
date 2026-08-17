package com.portfolio.agent.answer.routing.gateway;

import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TurnProposal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Vendor-neutral boundary for proposing a closed interpretation of one current turn. */
public interface TurnInterpretationPort {

    TurnInterpretationResult interpret(TurnInterpretationInput input);

    final class TurnInterpretationInput {

        private static final int MAX_CURRENT_INPUT_LENGTH = 4_000;

        private final String currentInput;
        private final List<SubjectReference> publicSubjects;
        private final Set<SemanticTaskType> allowedTaskTypes;
        private final List<SubjectReference> confirmedSubjects;
        private final List<SubjectReference> pendingInteractionSubjects;
        private final SubjectReference pageHint;
        private final List<PublicSubjectDescriptor> publicSubjectDescriptors;

        public TurnInterpretationInput(
                String currentInput,
                List<SubjectReference> publicSubjects,
                Set<SemanticTaskType> allowedTaskTypes) {
            this(currentInput, publicSubjects, allowedTaskTypes, List.of());
        }

        public TurnInterpretationInput(
                String currentInput,
                List<SubjectReference> publicSubjects,
                Set<SemanticTaskType> allowedTaskTypes,
                List<SubjectReference> confirmedSubjects) {
            this(currentInput, publicSubjects, allowedTaskTypes, confirmedSubjects, List.of());
        }

        public TurnInterpretationInput(
                String currentInput,
                List<SubjectReference> publicSubjects,
                Set<SemanticTaskType> allowedTaskTypes,
                List<SubjectReference> confirmedSubjects,
                List<SubjectReference> pendingInteractionSubjects) {
            this(currentInput, publicSubjects, allowedTaskTypes, confirmedSubjects, pendingInteractionSubjects, null);
        }

        public TurnInterpretationInput(
                String currentInput,
                List<SubjectReference> publicSubjects,
                Set<SemanticTaskType> allowedTaskTypes,
                List<SubjectReference> confirmedSubjects,
                List<SubjectReference> pendingInteractionSubjects,
                SubjectReference pageHint) {
            this(currentInput, publicSubjects, allowedTaskTypes, confirmedSubjects,
                    pendingInteractionSubjects, pageHint, publicSubjects.stream()
                            .map(subject -> new PublicSubjectDescriptor(subject, Set.of(subject.getSubjectId())))
                            .toList());
        }

        public TurnInterpretationInput(
                String currentInput, List<SubjectReference> publicSubjects,
                Set<SemanticTaskType> allowedTaskTypes, List<SubjectReference> confirmedSubjects,
                List<SubjectReference> pendingInteractionSubjects, SubjectReference pageHint,
                List<PublicSubjectDescriptor> publicSubjectDescriptors) {
            if (currentInput == null || currentInput.isBlank() || currentInput.length() > MAX_CURRENT_INPUT_LENGTH) {
                throw new IllegalArgumentException("currentInput must be non-blank and within the supported length");
            }
            this.currentInput = currentInput.trim();
            this.publicSubjects = copyDistinct(publicSubjects, "publicSubjects");
            this.allowedTaskTypes = Set.copyOf(Objects.requireNonNull(allowedTaskTypes, "allowedTaskTypes"));
            if (this.allowedTaskTypes.isEmpty()) {
                throw new IllegalArgumentException("allowedTaskTypes is required");
            }
            this.confirmedSubjects = copyDistinct(confirmedSubjects, "confirmedSubjects");
            for (SubjectReference subject : this.confirmedSubjects) {
                if (!this.publicSubjects.contains(subject)) {
                    throw new IllegalArgumentException("confirmedSubjects must belong to the public catalog");
                }
            }
            this.pendingInteractionSubjects = copyDistinct(pendingInteractionSubjects, "pendingInteractionSubjects");
            for (SubjectReference subject : this.pendingInteractionSubjects) {
                if (!this.publicSubjects.contains(subject)) {
                    throw new IllegalArgumentException("pendingInteractionSubjects must belong to the public catalog");
                }
            }
            if (pageHint != null && !this.publicSubjects.contains(pageHint)) {
                throw new IllegalArgumentException("pageHint must belong to the public catalog");
            }
            this.pageHint = pageHint;
            this.publicSubjectDescriptors = List.copyOf(Objects.requireNonNull(
                    publicSubjectDescriptors, "publicSubjectDescriptors"));
            if (this.publicSubjectDescriptors.size() != this.publicSubjects.size()
                    || !this.publicSubjectDescriptors.stream().map(PublicSubjectDescriptor::getSubject)
                    .collect(java.util.stream.Collectors.toSet()).equals(Set.copyOf(this.publicSubjects))) {
                throw new IllegalArgumentException("publicSubjectDescriptors must exactly describe publicSubjects");
            }
        }

        public String getCurrentInput() { return currentInput; }
        public List<SubjectReference> getPublicSubjects() { return publicSubjects; }
        public Set<SemanticTaskType> getAllowedTaskTypes() { return allowedTaskTypes; }
        public List<SubjectReference> getConfirmedSubjects() { return confirmedSubjects; }
        public List<SubjectReference> getPendingInteractionSubjects() { return pendingInteractionSubjects; }
        public Optional<SubjectReference> getPageHint() { return Optional.ofNullable(pageHint); }
        public List<PublicSubjectDescriptor> getPublicSubjectDescriptors() { return publicSubjectDescriptors; }
        public Optional<PublicSubjectDescriptor> describe(SubjectReference subject) {
            return publicSubjectDescriptors.stream().filter(value -> value.getSubject().equals(subject)).findFirst();
        }
    }

    final class PublicSubjectDescriptor {
        private final SubjectReference subject;
        private final Set<String> reviewedAliases;

        public PublicSubjectDescriptor(SubjectReference subject, Set<String> reviewedAliases) {
            this.subject = Objects.requireNonNull(subject, "subject");
            this.reviewedAliases = Set.copyOf(Objects.requireNonNull(reviewedAliases, "reviewedAliases"));
            if (this.reviewedAliases.isEmpty() || this.reviewedAliases.stream().anyMatch(
                    alias -> alias == null || alias.isBlank())) {
                throw new IllegalArgumentException("reviewedAliases are required");
            }
        }
        public SubjectReference getSubject() { return subject; }
        public Set<String> getReviewedAliases() { return reviewedAliases; }
    }

    final class TurnInterpretationResult {

        private final TurnProposal proposal;
        private final ConversationModelFailureCode failureCode;

        private TurnInterpretationResult(TurnProposal proposal, ConversationModelFailureCode failureCode) {
            this.proposal = proposal;
            this.failureCode = failureCode;
            if ((proposal == null) == (failureCode == null)) {
                throw new IllegalArgumentException("result must contain exactly one outcome");
            }
        }

        public static TurnInterpretationResult success(TurnProposal proposal) {
            return new TurnInterpretationResult(Objects.requireNonNull(proposal, "proposal"), null);
        }

        public static TurnInterpretationResult failure(ConversationModelFailureCode failureCode) {
            return new TurnInterpretationResult(null, Objects.requireNonNull(failureCode, "failureCode"));
        }

        public boolean isSuccessful() { return proposal != null; }
        public Optional<TurnProposal> getProposal() { return Optional.ofNullable(proposal); }
        public ConversationModelFailureCode getFailureCode() { return failureCode; }
    }

    private static List<SubjectReference> copyDistinct(List<SubjectReference> values, String name) {
        List<SubjectReference> copied = List.copyOf(Objects.requireNonNull(values, name));
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return copied;
    }
}
