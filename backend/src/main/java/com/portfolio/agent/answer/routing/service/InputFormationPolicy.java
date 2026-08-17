package com.portfolio.agent.answer.routing.service;

/** Determines whether the current question contains enough language to enter semantic routing. */
public final class InputFormationPolicy {

    public enum Formation {
        FORMED,
        UNFORMED
    }

    public Formation evaluate(String question) {
        if (question == null || question.isBlank()) {
            return Formation.UNFORMED;
        }
        return question.codePoints().anyMatch(Character::isLetter)
                ? Formation.FORMED
                : Formation.UNFORMED;
    }
}
