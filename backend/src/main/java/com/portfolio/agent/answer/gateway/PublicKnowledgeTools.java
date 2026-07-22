package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;

public interface PublicKnowledgeTools {

    PublicToolResult execute(RuntimeAnswerContent content, ToolCall call);
}
