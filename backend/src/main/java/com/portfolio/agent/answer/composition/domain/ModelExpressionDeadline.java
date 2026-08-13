package com.portfolio.agent.answer.composition.domain;
import java.time.Duration; import java.time.Instant; import java.util.Objects;
public final class ModelExpressionDeadline { private final Instant deadline; public ModelExpressionDeadline(Instant deadline){this.deadline=Objects.requireNonNull(deadline);} public Instant getDeadline(){return deadline;} public Duration remaining(Instant now){return Duration.between(now,deadline);} }
