package eu.dietwise.v1.model;

import java.util.Map;
import java.util.Set;

import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RecommendationWeight;
import org.immutables.value.Value;

@Value.Immutable
public interface ScoringData {
	int getTotalNumberOfRecomendations();

	Map<Recommendation, RecommendationWeight> getRecommendationWeights();

	Map<IngredientId, Set<Recommendation>> getRecommendationsPerIngredient();
}
