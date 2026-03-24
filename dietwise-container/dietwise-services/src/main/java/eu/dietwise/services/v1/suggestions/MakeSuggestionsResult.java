package eu.dietwise.services.v1.suggestions;

import java.util.Map;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.types.IngredientId;

public record MakeSuggestionsResult(
		SuggestionsRecipeAssessmentMessage message,
		Map<IngredientId, Set<RecommendationComponent>> recommendations
) {
	public MakeSuggestionsResult {
		recommendations = recommendations.entrySet().stream()
				.collect(java.util.stream.Collectors.toUnmodifiableMap(
						Map.Entry::getKey,
						entry -> Set.copyOf(entry.getValue())
				));
	}

	@Override
	public Map<IngredientId, Set<RecommendationComponent>> recommendations() {
		return recommendations.entrySet().stream()
				.collect(java.util.stream.Collectors.toUnmodifiableMap(
						Map.Entry::getKey,
						entry -> Set.copyOf(entry.getValue())
				));
	}
}
