package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class EvalSemanticTurnShapeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void capturesOnlySemanticStructureFromAPartialTurn() throws Exception {
        JsonNode agentTurn = mapper.readTree("""
                {
                  "contractVersion": "public-agent-turn",
                  "disposition": "PARTIAL_READY",
                  "plan": {
                    "taskCount": 3,
                    "tasks": [
                      {"goalLabel": "project-a", "sourceDomain": "PORTFOLIO"},
                      {"goalLabel": "compare", "sourceDomain": "GENERAL",
                       "dependencySummary": "requires 01, 02"},
                      {"goalLabel": "summary", "sourceDomain": "SYNTHESIS",
                       "dependencySummary": "requires 02"}
                    ]
                  },
                  "outcome": {
                    "planOutcome": "PARTIAL",
                    "taskSummary": {
                      "totalCount": 3,
                      "answeredCount": 1,
                      "blockedCount": 1,
                      "failedCount": 0,
                      "fallbackCount": 1,
                      "items": [
                        {"displayIndex": "01", "goalLabel": "project-a", "status": "COMPLETED", "sourceDomain": "PORTFOLIO"},
                        {"displayIndex": "02", "goalLabel": "compare", "status": "BLOCKED", "sourceDomain": "GENERAL"},
                        {"displayIndex": "03", "goalLabel": "summary", "status": "INSUFFICIENT", "sourceDomain": "SYNTHESIS"}
                      ]
                    }
                  },
                  "completedTasks": [
                    {"displayIndex": "01", "goalLabel": "project-a", "sourceDomain": "PORTFOLIO",
                     "resultPayload": {"kind": "SECTION", "blocks": [{"content": "sensitive answer body"}]}}
                  ]
                }
                """);

        EvalSemanticTurnShape shape = EvalSemanticTurnShape.from(agentTurn);

        assertThat(shape.getDisposition()).isEqualTo(EvalSemanticTurnShape.Disposition.PARTIAL_READY);
        assertThat(shape.getCoverageOutcome()).isEqualTo(EvalSemanticTurnShape.CoverageOutcome.PARTIAL);
        assertThat(shape.getTaskCount()).isEqualTo(3);
        assertThat(shape.getDependencyCount()).isEqualTo(3);
        assertThat(shape.getAnsweredCount()).isEqualTo(1);
        assertThat(shape.getBlockedCount()).isEqualTo(1);
        assertThat(shape.getFailedCount()).isZero();
        assertThat(shape.getFallbackCount()).isEqualTo(1);
        assertThat(shape.getPortfolioSourceTaskCount()).isEqualTo(1);
        assertThat(shape.getGeneralSourceTaskCount()).isEqualTo(1);
        assertThat(shape.getSynthesisSourceTaskCount()).isEqualTo(1);
        assertThat(shape.isPlanInvariantValid()).isTrue();
        assertThat(shape.isProvenanceValid()).isTrue();
        assertThat(shape.isPrivacySafe()).isTrue();
    }

    @Test
    void detectsInternalIdsWithoutRetainingThem() throws Exception {
        JsonNode agentTurn = mapper.readTree("""
                {"disposition":"READY","plan":{"taskCount":1,"tasks":[
                  {"taskId":"task-01","goalLabel":"project-a","sourceDomain":"PORTFOLIO"}
                ]}}
                """);

        EvalSemanticTurnShape shape = EvalSemanticTurnShape.from(agentTurn);

        assertThat(shape.isPrivacySafe()).isFalse();
        assertThat(shape.toString()).doesNotContain("task-01");
        assertThat(shape.toString()).doesNotContain("project-a");
        for (Field field : shape.getClass().getDeclaredFields()) {
            assertThat(field.getType()).isNotEqualTo(String.class);
        }
    }
}


