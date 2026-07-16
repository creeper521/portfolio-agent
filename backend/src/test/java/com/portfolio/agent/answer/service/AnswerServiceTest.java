package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerMode;
import com.portfolio.agent.answer.domain.AnswerResult;
import com.portfolio.agent.answer.engine.AnswerEngine;
import com.portfolio.agent.answer.exception.AnswerErrorCode;
import com.portfolio.agent.answer.exception.AnswerProjectNotFoundException;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerServiceTest {

    @Test
    void delegatesResolvedKnowledgeToEngine() {
        AnswerKnowledge knowledge = knowledge();
        AnswerResult expected = new AnswerResult(
                AnswerMode.DETERMINISTIC,
                true,
                false,
                "SQL Audit",
                List.of(),
                List.of(),
                List.of()
        );
        FakeKnowledgeGateway gateway = new FakeKnowledgeGateway(Optional.of(knowledge));
        CapturingAnswerEngine engine = new CapturingAnswerEngine(expected);
        AnswerService service = new AnswerService(gateway, engine);

        AnswerResult result = service.answer("sql-audit", "介绍项目");

        assertThat(result).isSameAs(expected);
        assertThat(gateway.requestedSlug).isEqualTo("sql-audit");
        assertThat(engine.knowledge).isSameAs(knowledge);
        assertThat(engine.question).isEqualTo("介绍项目");
    }

    @Test
    void throwsAnswerOwnedNotFoundExceptionWhenGatewayReturnsEmpty() {
        FakeKnowledgeGateway gateway = new FakeKnowledgeGateway(Optional.empty());
        CapturingAnswerEngine engine = new CapturingAnswerEngine(null);
        AnswerService service = new AnswerService(gateway, engine);

        assertThatThrownBy(() -> service.answer("missing-project", "介绍项目"))
                .isInstanceOf(AnswerProjectNotFoundException.class)
                .hasMessage("公开项目不存在: missing-project")
                .extracting(exception -> ((AnswerProjectNotFoundException) exception)
                        .getErrorCode())
                .isEqualTo(AnswerErrorCode.PROJECT_NOT_FOUND);
        assertThat(engine.knowledge).isNull();
    }

    private AnswerKnowledge knowledge() {
        return new AnswerKnowledge(
                "sql-audit",
                "SQL Audit",
                "Background",
                List.of(),
                "Solution",
                List.of(),
                List.of(),
                "Outcome",
                "Handoff",
                "DELIVERED",
                List.of(),
                List.of()
        );
    }

    private static final class FakeKnowledgeGateway implements PortfolioKnowledgeGateway {

        private final Optional<AnswerKnowledge> result;
        private String requestedSlug;

        private FakeKnowledgeGateway(Optional<AnswerKnowledge> result) {
            this.result = result;
        }

        @Override
        public Optional<AnswerKnowledge> findBySlug(String projectSlug) {
            requestedSlug = projectSlug;
            return result;
        }
    }

    private static final class CapturingAnswerEngine implements AnswerEngine {

        private final AnswerResult result;
        private AnswerKnowledge knowledge;
        private String question;

        private CapturingAnswerEngine(AnswerResult result) {
            this.result = result;
        }

        @Override
        public AnswerResult answer(AnswerKnowledge knowledge, String question) {
            this.knowledge = knowledge;
            this.question = question;
            return result;
        }
    }
}
