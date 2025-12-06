package eu.dietwise.services.v1;

import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import io.smallrye.mutiny.Multi;

public interface RecipeAssessmentService {
	Multi<RecipeAssessmentMessage> assessHtmlRecipe(RecipeAssessmentParam param);

	Multi<RecipeAssessmentMessage> assessMarkdownRecipe(RecipeAssessmentParam param);
}
