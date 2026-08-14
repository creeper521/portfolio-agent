package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.ResolvedSubjectBinding;
import com.portfolio.agent.answer.routing.domain.SubjectBindingRole;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves subjects only from the canonical structured sources and the reviewed
 * public catalog. It intentionally has no conversation-message dependency.
 */
public final class RoutingContextResolver {

    private final LegacySemanticContextAdapter legacyAdapter;

    public RoutingContextResolver(LegacySemanticContextAdapter legacyAdapter) {
        this.legacyAdapter = Objects.requireNonNull(legacyAdapter, "legacyAdapter");
    }

    public ResolvedRoutingContext resolve(SemanticTurnInput input, PublicSubjectCatalog catalog) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(catalog, "catalog");
        LegacySemanticContextAdapter.ContextMergeResult merge = legacyAdapter.merge(
                input.getSemanticContext(), input.getLegacyContext());
        if (merge.isConflict()) {
            return ResolvedRoutingContext.invalidInput("ROUTING_CONTEXT_CONFLICT");
        }
        SemanticContext context = merge.getContext();

        ResolvedRoutingContext explicitResult = resolveStructured(
                input.getExplicitResultReferences(), SubjectResolutionSource.STRUCTURED_RESULT, catalog, context);
        if (explicitResult != null) {
            return explicitResult;
        }
        ResolvedRoutingContext explicitSubject = resolveStructured(
                input.getExplicitSubjectReferences(), SubjectResolutionSource.EXPLICIT_REFERENCE, catalog, context);
        if (explicitSubject != null) {
            return explicitSubject;
        }

        List<PublicSubjectCatalog.Subject> questionMatches = catalog.matchQuestion(input.getQuestion());
        if (questionMatches.size() == 1) {
            return ResolvedRoutingContext.resolved(
                    List.of(catalog.toReference(questionMatches.get(0), SubjectResolutionSource.EXPLICIT_TEXT)),
                    SubjectResolutionSource.EXPLICIT_TEXT,
                    context);
        }
        if (questionMatches.size() > 1) {
            if (context.getActiveSubjects().size() == 1) {
                ResolvedRoutingContext activeSubject = resolveStructured(
                        context.getActiveSubjects(), SubjectResolutionSource.ACTIVE_SUBJECT, catalog, context);
                if (activeSubject != null) {
                    return activeSubject;
                }
            }
            return ResolvedRoutingContext.ambiguous("ROUTING_SUBJECT_AMBIGUOUS", context);
        }

        boolean contextDemand = isContextDemand(input.getRoutingQuestion());
        ResolvedRoutingContext pendingPlan = contextDemand
                ? context.getPendingPlanReference()
                        .map(reference -> resolveStructured(
                                reference.getSubjects(), SubjectResolutionSource.PENDING_PLAN, catalog, context))
                        .orElse(null)
                : null;
        if (pendingPlan != null) {
            return pendingPlan;
        }
        boolean bareContinuation = input.getRoutingQuestion() != null
                && input.getRoutingQuestion().trim().equals("\u7ee7\u7eed");
        ResolvedRoutingContext recentResult = contextDemand && !bareContinuation
                ? resolveStructured(context.getResultReferences(),
                        SubjectResolutionSource.STRUCTURED_RESULT, catalog, context)
                : null;
        if (recentResult != null) {
            return recentResult;
        }
        ResolvedRoutingContext pageSubject = resolvePageHintOrDeictic(input, catalog, context);
        if (pageSubject != null) {
            return pageSubject;
        }

        if (isDeictic(input.getRoutingQuestion())) {
            if (context.getActiveSubjects().size() == 1) {
                return resolveStructured(
                        context.getActiveSubjects(), SubjectResolutionSource.ACTIVE_SUBJECT,
                        SubjectBindingRole.DEICTIC, catalog, context);
            }
            if (context.getActiveSubjects().size() > 1) {
                return ResolvedRoutingContext.ambiguous("ROUTING_SUBJECT_AMBIGUOUS", context);
            }
        }
        if (!context.getActiveSubjects().isEmpty()) {
            ResolvedRoutingContext activeValidation = resolveStructured(
                    context.getActiveSubjects(), SubjectResolutionSource.ACTIVE_SUBJECT,
                    SubjectBindingRole.HINT, catalog, context);
            if (activeValidation != null
                    && "ROUTING_SUBJECT_INVALID_REFERENCE".equals(activeValidation.getReasonCode())) {
                return activeValidation;
            }
        }
        return ResolvedRoutingContext.unresolved("ROUTING_SUBJECT_UNRESOLVED", context);
    }

    /**
     * The model may only propose candidates after deterministic resolution was
     * insufficient. A single catalog-validated candidate is required.
     */
    public ResolvedRoutingContext resolveValidatedModelCandidates(
            ResolvedRoutingContext unresolvedContext,
            List<SubjectReference> candidates,
            PublicSubjectCatalog catalog) {
        Objects.requireNonNull(unresolvedContext, "unresolvedContext");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(catalog, "catalog");
        if (unresolvedContext.getStatus() != RoutingContextStatus.UNRESOLVED) {
            return unresolvedContext;
        }
        if (candidates.size() != 1) {
            return candidates.isEmpty()
                    ? ResolvedRoutingContext.unresolved(
                            "ROUTING_SUBJECT_UNRESOLVED", unresolvedContext.getContext())
                    : ResolvedRoutingContext.ambiguous(
                            "ROUTING_SUBJECT_AMBIGUOUS", unresolvedContext.getContext());
        }
        Optional<SubjectReference> validated = catalog.validate(
                candidates.get(0), SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE);
        if (validated.isEmpty()) {
            return ResolvedRoutingContext.unresolved(
                    "ROUTING_SUBJECT_INVALID_REFERENCE", unresolvedContext.getContext());
        }
        return ResolvedRoutingContext.resolved(
                List.of(validated.orElseThrow()),
                SubjectResolutionSource.VALIDATED_MODEL_CANDIDATE,
                unresolvedContext.getContext());
    }

    private ResolvedRoutingContext resolvePageHintOrDeictic(
            SemanticTurnInput input,
            PublicSubjectCatalog catalog,
            SemanticContext context) {
        if (input.getPageSubjects().isEmpty()) {
            return null;
        }
        SubjectBindingRole role = isDeictic(input.getRoutingQuestion())
                ? SubjectBindingRole.DEICTIC : SubjectBindingRole.HINT;
        if (role == SubjectBindingRole.HINT) {
            List<ResolvedSubjectBinding> hints = validateBindings(
                    input.getPageSubjects(), SubjectResolutionSource.PAGE_CONTEXT,
                    SubjectBindingRole.HINT, catalog);
            return ResolvedRoutingContext.unresolvedWithBindings(
                    "ROUTING_SUBJECT_UNRESOLVED", context, hints);
        }
        return resolveStructured(input.getPageSubjects(), SubjectResolutionSource.PAGE_CONTEXT,
                role, catalog, context);
    }

    private boolean isDeictic(String question) {
        if (question == null) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("这个项目") || normalized.contains("该项目")
                || normalized.contains("这个案例") || normalized.contains("该案例")
                || normalized.contains("this project") || normalized.contains("that project")
                || normalized.contains("this case") || normalized.contains("that case")
                || normalized.matches(".*\\b(it|this|that)\\b.*")
                || normalized.contains("它");
    }

    private boolean isContextDemand(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT).trim();
        return normalized.contains("\u7ee7\u7eed")
                || normalized.contains("\u57fa\u4e8e\u4e0a\u9762")
                || normalized.contains("\u6839\u636e\u4e0a\u9762")
                || normalized.contains("\u521a\u624d\u7684\u7ed3\u679c")
                || normalized.contains("\u8fd9\u4e2a\u7ed3\u679c")
                || normalized.contains("\u8fd9\u4e9b\u7ed3\u679c")
                || normalized.contains("\u8c03\u6574")
                || normalized.contains("\u518d\u8bf4")
                || normalized.contains("\u518d\u8bb2")
                || normalized.contains("continue")
                || normalized.contains("based on the result");
    }

    private ResolvedRoutingContext resolveStructured(
            List<SubjectReference> references,
            SubjectResolutionSource resolutionSource,
            PublicSubjectCatalog catalog,
            SemanticContext context) {
        return resolveStructured(references, resolutionSource,
                roleFor(resolutionSource), catalog, context);
    }

    private ResolvedRoutingContext resolveStructured(
            List<SubjectReference> references,
            SubjectResolutionSource resolutionSource,
            SubjectBindingRole bindingRole,
            PublicSubjectCatalog catalog,
            SemanticContext context) {
        if (references.isEmpty()) {
            return null;
        }
        List<SubjectReference> validated = new ArrayList<>();
        for (SubjectReference reference : references) {
            if (reference.getSubjectType() == SubjectType.RESULT
                    && resolutionSource == SubjectResolutionSource.STRUCTURED_RESULT) {
                validated.add(new SubjectReference(
                        SubjectType.RESULT, reference.getSubjectId(), resolutionSource,
                        reference.getContentVersion()));
                continue;
            }
            Optional<SubjectReference> catalogReference = catalog.validate(reference, resolutionSource);
            if (catalogReference.isEmpty()) {
                return ResolvedRoutingContext.unresolved("ROUTING_SUBJECT_INVALID_REFERENCE", context);
            }
            validated.add(catalogReference.orElseThrow());
        }
        return ResolvedRoutingContext.resolved(validated, resolutionSource, bindingRole, context);
    }

    private List<ResolvedSubjectBinding> validateBindings(
            List<SubjectReference> references,
            SubjectResolutionSource resolutionSource,
            SubjectBindingRole role,
            PublicSubjectCatalog catalog) {
        List<ResolvedSubjectBinding> bindings = new ArrayList<>();
        for (SubjectReference reference : references) {
            Optional<SubjectReference> validated = catalog.validate(reference, resolutionSource);
            if (validated.isPresent()) {
                bindings.add(new ResolvedSubjectBinding(validated.orElseThrow(), role, resolutionSource));
            }
        }
        return List.copyOf(bindings);
    }

    private SubjectBindingRole roleFor(SubjectResolutionSource source) {
        return switch (source) {
            case EXPLICIT_REFERENCE, EXPLICIT_TEXT, VALIDATED_MODEL_CANDIDATE -> SubjectBindingRole.EXPLICIT;
            case ACTIVE_SUBJECT, PAGE_CONTEXT -> SubjectBindingRole.HINT;
            case STRUCTURED_RESULT, PENDING_PLAN -> SubjectBindingRole.RESULT_BOUND;
        };
    }
}

enum RoutingContextStatus {
    RESOLVED,
    UNRESOLVED,
    AMBIGUOUS,
    INVALID_INPUT
}

final class ResolvedRoutingContext {

    private final RoutingContextStatus status;
    private final List<SubjectReference> subjects;
    private final SubjectResolutionSource resolutionSource;
    private final String reasonCode;
    private final SemanticContext context;
    private final List<ResolvedSubjectBinding> bindings;

    private ResolvedRoutingContext(
            RoutingContextStatus status,
            List<SubjectReference> subjects,
            SubjectResolutionSource resolutionSource,
            String reasonCode,
            SemanticContext context,
            List<ResolvedSubjectBinding> bindings) {
        this.status = Objects.requireNonNull(status, "status");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.resolutionSource = resolutionSource;
        this.reasonCode = reasonCode;
        this.context = Objects.requireNonNull(context, "context");
        this.bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
        boolean resolved = status == RoutingContextStatus.RESOLVED;
        if (resolved != (resolutionSource != null && !subjects.isEmpty())) {
            throw new IllegalArgumentException("resolved context must carry subjects and a source");
        }
        if (!resolved && (!subjects.isEmpty() || resolutionSource != null || reasonCode == null)) {
            throw new IllegalArgumentException("unresolved context must carry only a safe reason code");
        }
    }

    static ResolvedRoutingContext resolved(
            List<SubjectReference> subjects,
            SubjectResolutionSource source,
            SemanticContext context) {
        SubjectBindingRole role = source == SubjectResolutionSource.PAGE_CONTEXT
                ? SubjectBindingRole.HINT : source == SubjectResolutionSource.ACTIVE_SUBJECT
                ? SubjectBindingRole.DEICTIC : source == SubjectResolutionSource.STRUCTURED_RESULT
                || source == SubjectResolutionSource.PENDING_PLAN
                ? SubjectBindingRole.RESULT_BOUND : SubjectBindingRole.EXPLICIT;
        List<ResolvedSubjectBinding> bindings = subjects.stream()
                .map(subject -> new ResolvedSubjectBinding(subject, role, source)).toList();
        return resolved(subjects, source, role, context);
    }

    static ResolvedRoutingContext resolved(
            List<SubjectReference> subjects,
            SubjectResolutionSource source,
            SubjectBindingRole role,
            SemanticContext context) {
        List<ResolvedSubjectBinding> bindings = subjects.stream()
                .map(subject -> new ResolvedSubjectBinding(subject, role, source)).toList();
        return new ResolvedRoutingContext(RoutingContextStatus.RESOLVED, subjects, source, null, context, bindings);
    }

    static ResolvedRoutingContext unresolved(String reasonCode, SemanticContext context) {
        return new ResolvedRoutingContext(
                RoutingContextStatus.UNRESOLVED, List.of(), null, reasonCode, context, List.of());
    }

    static ResolvedRoutingContext ambiguous(String reasonCode, SemanticContext context) {
        return new ResolvedRoutingContext(
                RoutingContextStatus.AMBIGUOUS, List.of(), null, reasonCode, context, List.of());
    }

    static ResolvedRoutingContext invalidInput(String reasonCode) {
        return new ResolvedRoutingContext(
                RoutingContextStatus.INVALID_INPUT, List.of(), null, reasonCode, SemanticContext.empty(), List.of());
    }

    static ResolvedRoutingContext unresolvedWithBindings(
            String reasonCode, SemanticContext context, List<ResolvedSubjectBinding> bindings) {
        return new ResolvedRoutingContext(
                RoutingContextStatus.UNRESOLVED, List.of(), null, reasonCode, context, bindings);
    }

    RoutingContextStatus getStatus() {
        return status;
    }

    List<SubjectReference> getSubjects() {
        return subjects;
    }

    List<ResolvedSubjectBinding> getBindings() { return bindings; }

    SubjectResolutionSource getResolutionSource() {
        return resolutionSource;
    }

    String getReasonCode() {
        return reasonCode;
    }

    SemanticContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return "ResolvedRoutingContext{status=" + status
                + ", subjectCount=" + subjects.size()
                + ", resolutionSource=" + resolutionSource
                + ", reasonCode=" + reasonCode + '}';
    }
}

final class PublicSubjectCatalog {

    private final Map<SubjectKey, Subject> subjects;
    private final Map<String, SubjectKey> aliases;

    PublicSubjectCatalog(List<Subject> subjects) {
        Objects.requireNonNull(subjects, "subjects");
        Map<SubjectKey, Subject> copied = new LinkedHashMap<>();
        Map<String, SubjectKey> copiedAliases = new LinkedHashMap<>();
        Set<String> conflictedAliases = new LinkedHashSet<>();
        for (Subject subject : subjects) {
            Subject nonNullSubject = Objects.requireNonNull(subject, "subject");
            SubjectKey subjectKey = new SubjectKey(nonNullSubject.getSubjectType(), nonNullSubject.getSubjectId());
            Subject previous = copied.put(subjectKey, nonNullSubject);
            if (previous != null) {
                throw new IllegalArgumentException("public subject catalog must not contain duplicate subjects");
            }
            for (String alias : nonNullSubject.aliases) {
                if (conflictedAliases.contains(alias)) {
                    continue;
                }
                SubjectKey previousAlias = copiedAliases.putIfAbsent(alias, subjectKey);
                if (previousAlias != null && !previousAlias.equals(subjectKey)) {
                    copiedAliases.remove(alias);
                    conflictedAliases.add(alias);
                }
            }
        }
        this.subjects = Map.copyOf(copied);
        this.aliases = Map.copyOf(copiedAliases);
    }

    List<Subject> matchQuestion(String question) {
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.isEmpty()) {
            return List.of();
        }
        List<Subject> matches = new ArrayList<>();
        for (Subject subject : subjects.values()) {
            if (subject.matches(normalizedQuestion)) {
                matches.add(subject);
            }
        }
        matches.sort(Comparator.comparing(Subject::getSubjectId));
        return List.copyOf(matches);
    }

    Optional<SubjectReference> validate(
            SubjectReference reference,
            SubjectResolutionSource resolvedSource) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(resolvedSource, "resolvedSource");
        Subject subject = subjects.get(new SubjectKey(reference.getSubjectType(), reference.getSubjectId()));
        if (subject == null) {
            SubjectKey aliasKey = aliases.get(normalize(reference.getSubjectId()));
            if (aliasKey == null || aliasKey.subjectType != reference.getSubjectType()) {
                return Optional.empty();
            }
            subject = subjects.get(aliasKey);
        }
        return Optional.of(toReference(subject, resolvedSource));
    }

    SubjectReference toReference(Subject subject, SubjectResolutionSource resolutionSource) {
        return new SubjectReference(
                subject.getSubjectType(), subject.getSubjectId(),
                resolutionSource, subject.getContentVersion());
    }

    List<Subject> list(SubjectType requiredType) {
        return subjects.values().stream()
                .filter(subject -> requiredType == null || subject.getSubjectType() == requiredType)
                .sorted(Comparator.comparing(Subject::getDisplayLabel)
                        .thenComparing(Subject::getSubjectId))
                .toList();
    }

    List<SubjectReference> references() {
        return list(null).stream()
                .map(subject -> toReference(subject, SubjectResolutionSource.EXPLICIT_REFERENCE))
                .toList();
    }

    String currentContentVersion() {
        Set<String> versions = subjects.values().stream()
                .map(Subject::getContentVersion)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (versions.size() != 1) {
            throw new IllegalStateException("public subject catalog must bind one content version");
        }
        return versions.iterator().next();
    }

    static final class Subject {

        private final SubjectType subjectType;
        private final String subjectId;
        private final String contentVersion;
        private final String displayLabel;
        private final Set<String> aliases;

        Subject(SubjectType subjectType, String subjectId, String contentVersion, Set<String> aliases) {
            this(subjectType, subjectId, contentVersion, subjectId, aliases);
        }

        Subject(
                SubjectType subjectType, String subjectId, String contentVersion,
                String displayLabel, Set<String> aliases) {
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
            this.subjectId = requireText(subjectId, "subjectId");
            this.contentVersion = requireText(contentVersion, "contentVersion");
            this.displayLabel = requireText(displayLabel, "displayLabel");
            Objects.requireNonNull(aliases, "aliases");
            Set<String> normalizedAliases = new LinkedHashSet<>();
            normalizedAliases.add(normalize(this.subjectId));
            for (String alias : aliases) {
                String normalized = normalize(alias);
                if (normalized.isEmpty()) {
                    throw new IllegalArgumentException("aliases must not contain blank values");
                }
                normalizedAliases.add(normalized);
            }
            this.aliases = Set.copyOf(normalizedAliases);
        }

        SubjectType getSubjectType() {
            return subjectType;
        }

        String getSubjectId() {
            return subjectId;
        }

        String getContentVersion() {
            return contentVersion;
        }

        String getDisplayLabel() { return displayLabel; }

        boolean matches(String normalizedQuestion) {
            for (String alias : aliases) {
                if (normalizedQuestion.contains(alias)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class SubjectKey {

        private final SubjectType subjectType;
        private final String subjectId;

        private SubjectKey(SubjectType subjectType, String subjectId) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SubjectKey that)) {
                return false;
            }
            return subjectType == that.subjectType && Objects.equals(subjectId, that.subjectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjectType, subjectId);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
