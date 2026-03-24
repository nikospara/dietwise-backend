package eu.dietwise.services.v1.scoring.impl;

import java.util.Set;

import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecommendationComponentName;

public record IngredientIdAndRecommendations(
		IngredientId ingredientId,
		Set<RecommendationComponentName> recommendations
) {
	public IngredientIdAndRecommendations {
		recommendations = Set.copyOf(recommendations);
	}
}
