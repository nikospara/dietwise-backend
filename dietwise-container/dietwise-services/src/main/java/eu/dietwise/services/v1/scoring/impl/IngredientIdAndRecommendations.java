package eu.dietwise.services.v1.scoring.impl;

import java.util.Set;

import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.Recommendation;

public record IngredientIdAndRecommendations(IngredientId ingredientId, Set<Recommendation> recommendations) {
}
