package com.portfolio.agent.selection.mapper;

import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSelectionResult;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.dto.CapabilityCoverageResponse;
import com.portfolio.agent.selection.dto.ComplementarityResponse;
import com.portfolio.agent.selection.dto.EvidenceReferenceResponse;
import com.portfolio.agent.selection.dto.PortfolioSelectionAlternativeResponse;
import com.portfolio.agent.selection.dto.PortfolioSelectionItemResponse;
import com.portfolio.agent.selection.dto.PortfolioSelectionResponse;
import com.portfolio.agent.selection.dto.SelectionDegradationResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PortfolioSelectionResponseMapper {

    public PortfolioSelectionResponse map(
            PortfolioSelectionResult result,
            SelectionTarget target) {
        List<PortfolioSelectionItemResponse> items = result.getSelection().getCandidates().stream()
                .map(this::mapItem)
                .toList();
        return new PortfolioSelectionResponse(
                selectionId(result, target),
                result.getReleaseVersion(),
                result.getSelection().getPolicyVersion(),
                result.getRetrievalMode(),
                selectionMode(result.getSelection().getPolicyVersion()),
                result.getSelection().getStatus(),
                target.getRequestedSize(),
                items,
                coverage(result.getSelection().getCandidates(), target),
                complementarity(result.getSelection().getCandidates()),
                alternatives(result),
                degradation(result));
    }

    private PortfolioSelectionItemResponse mapItem(SelectionCandidate candidate) {
        List<EvidenceReferenceResponse> evidence = candidate.getEvidenceReferences().stream()
                .filter(EvidenceReference::isApproved)
                .sorted(Comparator
                        .comparing(EvidenceReference::getClaimId)
                        .thenComparing(EvidenceReference::getEvidenceId))
                .map(reference -> new EvidenceReferenceResponse(
                        reference.getClaimId(),
                        reference.getEvidenceId(),
                        reference.getLabel()))
                .toList();
        String capabilities = String.join(
                "、",
                candidate.getCapabilityCodes().stream().sorted().toList());
        String reason = capabilities.isEmpty()
                ? "提供已批准公开证据并补充资产组合视角"
                : "覆盖能力 " + capabilities + "，并提供已批准公开证据";
        return new PortfolioSelectionItemResponse(
                candidate.getSubjectId(),
                candidate.getSubjectKind(),
                candidate.getTitle(),
                candidate.getSummary(),
                candidate.getRoute(),
                candidate.getCareerTrack(),
                candidate.getCapabilityCodes(),
                reason,
                evidence);
    }

    private List<CapabilityCoverageResponse> coverage(
            List<SelectionCandidate> selected,
            SelectionTarget target) {
        Set<String> capabilityCodes = new HashSet<>(target.getCapabilityCodes());
        if (capabilityCodes.isEmpty()) {
            selected.forEach(candidate -> capabilityCodes.addAll(candidate.getCapabilityCodes()));
        }
        return capabilityCodes.stream()
                .sorted()
                .map(code -> new CapabilityCoverageResponse(
                        code,
                        code,
                        selected.stream()
                                .filter(candidate -> candidate.getCapabilityCodes().contains(code))
                                .map(SelectionCandidate::getSubjectId)
                                .sorted()
                                .toList()))
                .toList();
    }

    private List<ComplementarityResponse> complementarity(List<SelectionCandidate> selected) {
        List<ComplementarityResponse> responses = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < selected.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < selected.size(); rightIndex++) {
                SelectionCandidate left = selected.get(leftIndex);
                SelectionCandidate right = selected.get(rightIndex);
                Set<String> unique = new HashSet<>(left.getCapabilityCodes());
                unique.addAll(right.getCapabilityCodes());
                if (!unique.isEmpty()) {
                    responses.add(new ComplementarityResponse(
                            left.getSubjectId(),
                            right.getSubjectId(),
                            "共同覆盖互补能力：" + String.join("、", unique.stream().sorted().toList())));
                }
            }
        }
        return List.copyOf(responses);
    }

    private List<PortfolioSelectionAlternativeResponse> alternatives(PortfolioSelectionResult result) {
        Set<String> selectedIds = Set.copyOf(result.getSelection().getSubjectIds());
        return result.getCandidatePool().stream()
                .filter(candidate -> !selectedIds.contains(candidate.getSubjectId()))
                .limit(3)
                .map(candidate -> new PortfolioSelectionAlternativeResponse(
                        candidate.getSubjectId(),
                        candidate.getSubjectKind(),
                        candidate.getTitle(),
                        candidate.getSummary(),
                        candidate.getRoute(),
                        "候选有效，但当前组合的能力覆盖与互补性更高"))
                .toList();
    }

    private SelectionDegradationResponse degradation(PortfolioSelectionResult result) {
        String code = result.getReasonCode();
        if (code == null && result.getRetrievalMode() == RetrievalMode.FTS_ONLY) {
            code = "VECTOR_RETRIEVAL_UNAVAILABLE";
        }
        if (code == null) {
            return null;
        }
        String message = switch (code) {
            case "INSUFFICIENT_ELIGIBLE_ASSETS" -> "符合公开证据门槛的资产少于请求数量";
            case "CAPABILITY_COVERAGE_INCOMPLETE" -> "当前公开资产无法覆盖全部请求能力";
            case "PUBLIC_SELECTION_UNAVAILABLE" -> "组合推荐暂时不可用，请使用现有作品浏览入口";
            case "VECTOR_RETRIEVAL_UNAVAILABLE" -> "当前使用全文检索完成候选召回";
            default -> "组合推荐处于受控降级状态";
        };
        return new SelectionDegradationResponse(code, message);
    }

    private String selectionId(PortfolioSelectionResult result, SelectionTarget target) {
        String canonical = String.join(
                "\n",
                result.getReleaseVersion(),
                result.getSelection().getPolicyVersion(),
                result.getRetrievalMode().name(),
                nullSafe(target.getCareerTrack()),
                target.getAudienceRole(),
                String.join(",", target.getCapabilityCodes().stream().sorted().toList()),
                nullSafe(target.getGoal()),
                Integer.toString(target.getRequestedSize()),
                String.join(",", result.getSelection().getSubjectIds()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sel_" + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String selectionMode(String policyVersion) {
        return policyVersion.startsWith("top-k") ? "TOP_K" : "EXHAUSTIVE";
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
