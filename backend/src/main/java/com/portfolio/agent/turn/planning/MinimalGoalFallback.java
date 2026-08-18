package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MinimalGoalFallback {

    public Optional<UserGoalProposal> tryResolve(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            return Optional.empty();
        }
        String normalized = freeText.getText().trim();
        AgentTurnCommand.SubjectHint hint = command.getSurfaceContext().getSubjectHint();
        List<GoalInterpretationInput.PublicSubjectDescriptor> namedSubjects =
                context.getPublicSubjects().stream()
                        .filter(subject -> subject.getReviewedAliases().stream()
                                .anyMatch(alias -> normalized.contains(alias)))
                        .distinct().toList();
        if (normalized.contains("比较") && namedSubjects.size() == 2) {
            UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(
                    freeText.getText(), 0);
            List<GoalSubjectReference> references = namedSubjects.stream()
                    .map(subject -> new GoalSubjectReference(
                            subject.getKind(), subject.getReference(),
                            GoalSubjectReference.Basis.EXPLICIT_INPUT, anchor))
                    .toList();
            UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                    "portfolio-comparison", GoalKind.PORTFOLIO_COMPARE, anchor, references,
                    Set.of(GoalRequestedOutput.COMPARISON),
                    GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                    new UserGoalProposal.PortfolioCompareParameters(Set.of("IMPLEMENTATION")));
            return Optional.of(new UserGoalProposal(List.of(goal)));
        }
        for (GoalInterpretationInput.PublicSubjectDescriptor subject : context.getPublicSubjects()) {
            boolean exactText = subject.matchesAlias(normalized);
            boolean mentionsAlias = subject.getReviewedAliases().stream()
                    .anyMatch(alias -> normalized.contains(alias));
            boolean resolvedHint = hint != null
                    && hint.getKind().name().equals(subject.getKind().name())
                    && subject.matchesAlias(hint.getSlug())
                    && (mentionsAlias || referencesSurface(normalized));
            if (exactText || resolvedHint) {
                UserGoalProposal.InputAnchor anchor = new UserGoalProposal.InputAnchor(
                        exactText ? normalized : freeText.getText(), 0);
                GoalSubjectReference reference = new GoalSubjectReference(
                        subject.getKind(), subject.getReference(),
                        exactText ? GoalSubjectReference.Basis.EXPLICIT_INPUT
                                : GoalSubjectReference.Basis.SURFACE_HINT,
                        exactText ? anchor : null);
                UserGoalProposal.ProposedGoal goal = new UserGoalProposal.ProposedGoal(
                        "portfolio-overview", GoalKind.PORTFOLIO_FACT, anchor,
                        List.of(reference), Set.of(GoalRequestedOutput.OVERVIEW),
                        GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.OVERVIEW)));
                return Optional.of(new UserGoalProposal(List.of(goal)));
            }
        }
        return Optional.empty();
    }

    private boolean referencesSurface(String text) {
        return text.contains("这个项目") || text.contains("该项目")
                || text.contains("这个案例") || text.contains("该案例")
                || text.contains("这个作品") || text.contains("该作品");
    }
}
