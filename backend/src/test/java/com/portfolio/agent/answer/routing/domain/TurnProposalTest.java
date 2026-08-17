package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskDependencyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnProposalTest {

    @Test
    void executionRequiresOneToSixTasksWithDistinctLocalKeys() {
        TurnProposal.TaskProposal task = new TurnProposal.TaskProposal(
                "task-a", SemanticTaskType.GENERAL_EXPLANATION,
                new TextAnchor("explain", 1), List.of(), Set.of(RequestedOutput.SUMMARY));

        TurnProposal proposal = TurnProposal.execution(List.of(task));

        assertThat(proposal.getKind()).isEqualTo(TurnProposal.Kind.PROPOSE_EXECUTION);
        assertThat(proposal.getTasks()).containsExactly(task);
        assertThatThrownBy(() -> TurnProposal.execution(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurnProposal.execution(List.of(task, task)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clarificationAndConversationAreMutuallyExclusiveClosedKinds() {
        TurnProposal clarification = TurnProposal.clarification(
                new TurnProposal.Clarification("SUBJECT", new TextAnchor("project", 1)));
        TurnProposal conversation = TurnProposal.converse(
                TurnProposal.ConversationAct.UNINTERPRETABLE, List.of("preset-a"));

        assertThat(clarification.getTasks()).isEmpty();
        assertThat(clarification.getClarification()).isPresent();
        assertThat(conversation.getConversationAct()).contains(TurnProposal.ConversationAct.UNINTERPRETABLE);
        assertThatThrownBy(() -> TurnProposal.converse(
                TurnProposal.ConversationAct.UNINTERPRETABLE, List.of("unknown action")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subjectCandidatesAndLocalDependenciesRequireClosedBindingEvidence() {
        TurnProposal.SubjectCandidate explicit = new TurnProposal.SubjectCandidate(
                SubjectType.PROJECT, "project-a", TurnProposal.SubjectBasis.EXPLICIT_INPUT,
                new TextAnchor("project-a", 1), null, null);

        assertThat(explicit.getBasis()).isEqualTo(TurnProposal.SubjectBasis.EXPLICIT_INPUT);
        assertThatThrownBy(() -> new TurnProposal.SubjectCandidate(
                SubjectType.PROJECT, "project-a", TurnProposal.SubjectBasis.RECENT_RESULT,
                null, "results-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TurnProposal.ProposalDependency(
                "task-a", "task-a", TaskDependencyType.REQUIRES_SUCCESS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
