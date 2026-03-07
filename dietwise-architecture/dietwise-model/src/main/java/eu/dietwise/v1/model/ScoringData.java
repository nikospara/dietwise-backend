package eu.dietwise.v1.model;

import java.util.Map;
import java.util.Set;

import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.RecommendationWeight;
import org.immutables.value.Value;

@Value.Immutable
public interface ScoringData {
	int getTotalNumberOfRecomendations();

	Map<RecommendationComponentName, RecommendationWeight> getRecommendationWeights();

	Map<IngredientId, Set<RecommendationComponentName>> getRecommendationsPerIngredient();
}
