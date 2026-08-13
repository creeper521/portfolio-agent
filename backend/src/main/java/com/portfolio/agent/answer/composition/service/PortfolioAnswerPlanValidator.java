package com.portfolio.agent.answer.composition.service;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
public final class PortfolioAnswerPlanValidator { public void validate(PortfolioAnswerPlan plan,int characterLimit){if(plan==null)throw new IllegalArgumentException("plan");if(characterLimit<0)throw new IllegalArgumentException("characterLimit");int size=plan.getTitle().length()+(plan.getSummary()==null?0:plan.getSummary().length());for(PortfolioAnswerSection section:plan.getSections())size+=section.getContent().length();if(size>characterLimit)throw new IllegalArgumentException("plan exceeds character limit");} }
