package eu.dietwise.services.v1;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import io.smallrye.mutiny.Multi;

public interface RecipeAssessmentService {
	Multi<RecipeAssessmentMessage> assessMarkdownRecipe(User user, RecipeAssessmentParam param);

	Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(User user, RecipeExtractionAndAssessmentParam param);

	Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrlDummy(User user, RecipeExtractionAndAssessmentParam param);
}
