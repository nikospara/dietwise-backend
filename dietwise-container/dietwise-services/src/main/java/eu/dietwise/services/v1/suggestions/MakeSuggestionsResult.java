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
}
