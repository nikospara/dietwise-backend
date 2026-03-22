package eu.dietwise.services.v1.suggestions.impl;

import java.util.List;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.model.Rule;

record RulesAndComponents(
		List<Rule> rules,
		Set<RecommendationComponent> components
) {
}
