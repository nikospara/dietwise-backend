package eu.dietwise.services.v1.suggestions.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Suggestion;

public record SuggestionsAndComponents(
		Ingredient ingredient,
		List<Suggestion> suggestions,
		Set<RecommendationComponent> components
) {
	public SuggestionsAndComponents {
		suggestions = List.copyOf(suggestions);
		components = Set.copyOf(components);
	}

	public static SuggestionsAndComponents empty(Ingredient ingredient) {
		return new SuggestionsAndComponents(ingredient, Collections.emptyList(), Collections.emptySet());
	}
}
