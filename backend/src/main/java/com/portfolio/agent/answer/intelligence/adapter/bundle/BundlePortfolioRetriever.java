package com.portfolio.agent.answer.intelligence.adapter.bundle;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BundlePortfolioRetriever implements PortfolioRetriever {

    private static final PortfolioRetrievalSource SOURCE = new PortfolioRetrievalSource("BUNDLE");
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final PortfolioKnowledgeGateway knowledgeGateway;

    public BundlePortfolioRetriever(PortfolioKnowledgeGateway knowledgeGateway) {
        this.knowledgeGateway = Objects.requireNonNull(knowledgeGateway, "knowledgeGateway");
    }

    @Override
    public PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request) {
        Objects.requireNonNull(request, "request");
        RuntimeAnswerContent content = knowledgeGateway.getContent();
        List<SubjectMaterial> matched = allKnowledge(content).stream()
                .filter(knowledge -> matches(request.getQuery(), knowledge))
                .map(this::toMaterial)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(material -> material.subject().getSubjectId()))
                .limit(request.getLimit())
                .toList();
        List<PortfolioRetrievedSubject> subjects = matched.stream()
                .map(SubjectMaterial::subject)
                .toList();
        List<PortfolioRetrievedPassage> passages = matched.stream()
                .flatMap(material -> material.passages().stream())
                .toList();
        return new PortfolioRetrievalResult(
                content.getContentVersion(), subjects, passages, SOURCE, false, null);
    }

    private List<AnswerKnowledge> allKnowledge(RuntimeAnswerContent content) {
        List<AnswerKnowledge> all = new ArrayList<>(content.getProjects());
        all.addAll(content.getCases());
        return List.copyOf(all);
    }

    private SubjectMaterial toMaterial(AnswerKnowledge knowledge) {
        Set<String> approvedEvidenceIds = knowledge.getEvidence().stream()
                .filter(this::isApprovedPublicEvidence)
                .map(AnswerEvidence::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<AnswerClaimProjection> claims = knowledge.getClaims().stream()
                .filter(claim -> claim.getVerificationStatus() == AnswerClaimVerificationStatus.VERIFIED)
                .filter(claim -> !claim.getDirectEvidenceIds().isEmpty())
                .filter(claim -> approvedEvidenceIds.containsAll(claim.getDirectEvidenceIds()))
                .sorted(Comparator.comparing(AnswerClaimProjection::getId))
                .toList();
        if (claims.isEmpty()) {
            return null;
        }
        String routePrefix = knowledge.getSubjectType().name().equals("CASE") ? "/cases/" : "/projects/";
        PortfolioRetrievedSubject subject = new PortfolioRetrievedSubject(
                knowledge.getSlug(), knowledge.getSubjectType().name(), knowledge.getTitle(),
                knowledge.getSummary(), routePrefix + knowledge.getSlug(), Set.of());
        List<PortfolioRetrievedPassage> passages = claims.stream()
                .map(claim -> new PortfolioRetrievedPassage(
                        knowledge.getSlug() + "#" + claim.getId(), knowledge.getSlug(), claim.getId(),
                        passageContent(claim, knowledge), claim.getDirectEvidenceIds()))
                .toList();
        return new SubjectMaterial(subject, passages);
    }

    private boolean isApprovedPublicEvidence(AnswerEvidence evidence) {
        return "APPROVED".equals(evidence.getPublicStatus()) && !evidence.isRawContentPublic();
    }

    private String passageContent(AnswerClaimProjection claim, AnswerKnowledge knowledge) {
        return claim.getStatement() == null || claim.getStatement().isBlank()
                ? knowledge.getSummary() : claim.getStatement();
    }

    private boolean matches(String query, AnswerKnowledge knowledge) {
        Set<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return true;
        }
        StringBuilder searchable = new StringBuilder();
        append(searchable, knowledge.getTitle());
        append(searchable, knowledge.getSummary());
        for (AnswerClaimProjection claim : knowledge.getClaims()) {
            append(searchable, claim.getStatement());
            append(searchable, claim.getDetail());
            for (String topic : claim.getTopics()) {
                append(searchable, topic);
            }
        }
        String text = searchable.toString().toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(text::contains);
    }

    private Set<String> tokenize(String query) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            terms.add(matcher.group());
        }
        return Set.copyOf(terms);
    }

    private void append(StringBuilder builder, String value) {
        if (value != null) {
            builder.append(' ').append(value);
        }
    }

    private static final class SubjectMaterial {

        private final PortfolioRetrievedSubject subject;
        private final List<PortfolioRetrievedPassage> passages;

        private SubjectMaterial(PortfolioRetrievedSubject subject, List<PortfolioRetrievedPassage> passages) {
            this.subject = subject;
            this.passages = List.copyOf(passages);
        }

        private PortfolioRetrievedSubject subject() { return subject; }
        private List<PortfolioRetrievedPassage> passages() { return passages; }
    }
}
