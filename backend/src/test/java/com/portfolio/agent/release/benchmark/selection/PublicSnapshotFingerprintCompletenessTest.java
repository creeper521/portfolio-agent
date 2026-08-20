package com.portfolio.agent.release.benchmark.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicSnapshotFingerprintCompletenessTest {

    @Test
    void detectsEachPreviouslyOmittedRuntimeSnapshotFieldIndependently() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RuntimeContentSnapshot original = loadBundle(mapper);
        PublicSnapshotFingerprintFactory factory = new PublicSnapshotFingerprintFactory(mapper);
        Map<String, String> baseline = factory.create(original).getCanonicalHashes();

        OwnerProfile owner = original.getOwner();
        assertOnlyHashChanged(
                "owner", baseline, factory, copy(original,
                        new OwnerProfile(
                                owner.getName(), owner.getRole(), owner.getSummary() + " changed",
                                owner.getGithubUrl(), owner.getEmail(), owner.getResumeUrl()),
                        original.getPublishedAt(), original.getQuestions(), original.getTimeline(),
                        original.getRuntimeBundleHash()));
        assertOnlyHashChanged(
                "publishedAt", baseline, factory, copy(original,
                        original.getOwner(), original.getPublishedAt().plusDays(1),
                        original.getQuestions(), original.getTimeline(),
                        original.getRuntimeBundleHash()));
        assertOnlyHashChanged(
                "questions", baseline, factory, copy(original,
                        original.getOwner(), original.getPublishedAt(),
                        withoutFirst(original.getQuestions()), original.getTimeline(),
                        original.getRuntimeBundleHash()));
        assertOnlyHashChanged(
                "timeline", baseline, factory, copy(original,
                        original.getOwner(), original.getPublishedAt(),
                        original.getQuestions(), withoutFirst(original.getTimeline()),
                        original.getRuntimeBundleHash()));
        assertOnlyHashChanged(
                "runtimeBundleHash", baseline, factory, copy(original,
                        original.getOwner(), original.getPublishedAt(),
                        original.getQuestions(), original.getTimeline(),
                        "sha256:" + "0".repeat(64)));

        PublicSnapshotFingerprint fingerprint = factory.create(original);
        assertThat(fingerprint.getSemanticCounts())
                .containsEntry("QUESTION", original.getQuestions().size())
                .containsEntry("TIMELINE_EVENT", original.getTimeline().size());
        assertThat(fingerprint.getCanonicalHashes()).containsKeys(
                "owner", "publishedAt", "questions", "timeline", "runtimeBundleHash");
    }

    private void assertOnlyHashChanged(
            String expectedHash,
            Map<String, String> baseline,
            PublicSnapshotFingerprintFactory factory,
            RuntimeContentSnapshot changed) {
        Map<String, String> actual = factory.create(changed).getCanonicalHashes();
        assertThat(actual.get(expectedHash)).isNotEqualTo(baseline.get(expectedHash));
        assertThat(withoutKey(actual, expectedHash))
                .isEqualTo(withoutKey(baseline, expectedHash));
    }

    private Map<String, String> withoutKey(Map<String, String> source, String key) {
        Map<String, String> copy = new HashMap<>(source);
        copy.remove(key);
        return copy;
    }

    private RuntimeContentSnapshot copy(
            RuntimeContentSnapshot source,
            OwnerProfile owner,
            OffsetDateTime publishedAt,
            List<QuestionDefinition> questions,
            List<TimelineEvent> timeline,
            String runtimeBundleHash) {
        PortfolioSnapshot content = new PortfolioSnapshot(
                source.getSchemaVersion(),
                source.getContentVersion(),
                publishedAt,
                owner,
                source.getProjects(),
                source.getCases(),
                source.getCollections(),
                source.getClaims(),
                source.getClaimEvidenceLinks(),
                questions,
                source.getEvidence(),
                timeline);
        return new RuntimeContentSnapshot(
                content,
                runtimeBundleHash,
                source.getLoadedAt(),
                source.getRetrievalContent().orElse(null));
    }

    private <T> List<T> withoutFirst(List<T> values) {
        return values.subList(1, values.size());
    }

    private RuntimeContentSnapshot loadBundle(ObjectMapper mapper) throws Exception {
        Set<String> names = Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<>();
        Path root = Path.of("src/main/resources/public-data/bundle");
        for (String name : names) {
            files.put(name, Files.readAllBytes(root.resolve(name)));
        }
        return new PublicBundleLoader(
                mapper, new PortfolioSnapshotValidator(), Clock.systemUTC()).load(files);
    }
}
