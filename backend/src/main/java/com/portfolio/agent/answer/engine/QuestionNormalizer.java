package com.portfolio.agent.answer.engine;

import com.portfolio.agent.common.text.StableQuestionNormalizer;
import org.springframework.stereotype.Component;

@Component
public class QuestionNormalizer {

    public String normalize(String question) {
        return StableQuestionNormalizer.normalize(question);
    }
}
