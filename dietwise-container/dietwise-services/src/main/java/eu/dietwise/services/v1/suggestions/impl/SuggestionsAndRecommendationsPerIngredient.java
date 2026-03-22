package eu.dietwise.services.v1.suggestions.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.IngredientId;

record SuggestionsAndRecommendationsPerIngredient(
		List<Suggestion> suggestions,
		Map<IngredientId, Set<RecommendationComponent>> recommendations
) {
	public static SuggestionsAndRecommendationsPerIngredient emptyMutable() {
		return new SuggestionsAndRecommendationsPerIngredient(new ArrayList<>(), new HashMap<>());
	}

	public SuggestionsAndRecommendationsPerIngredient withSuggestions(List<Suggestion> newSuggestions) {
		return new SuggestionsAndRecommendationsPerIngredient(newSuggestions, recommendations);
	}
}
