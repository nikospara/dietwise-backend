package eu.dietwise.services.v1.scoring;

import java.util.Map;
import java.util.Set;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface RecipeScoringService {
	/**
	 * Build the scoring message for a recipe: every recommendation component known to the system, weighted for the age
	 * group and biological gender of the user, plus the components each ingredient of the recipe carries.
	 *
	 * @param personalInfo The profile of the user, may be {@code null} or partially filled in
	 */
	Uni<ScoringRecipeAssessmentMessage> makeScoringMessage(Map<IngredientId, Set<RecommendationComponent>> recommendations, RecipeLanguage lang, PersonalInfo personalInfo);
}
