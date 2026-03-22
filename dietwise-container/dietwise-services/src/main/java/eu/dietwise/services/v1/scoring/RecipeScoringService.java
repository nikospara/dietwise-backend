package eu.dietwise.services.v1.scoring;

import java.util.Map;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.types.IngredientId;
import io.smallrye.mutiny.Uni;

public interface RecipeScoringService {
	Uni<ScoringRecipeAssessmentMessage> makeScoringMessage(Map<IngredientId, Set<RecommendationComponent>> recommendations);
}
