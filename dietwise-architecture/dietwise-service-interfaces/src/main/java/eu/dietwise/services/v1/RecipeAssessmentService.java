package eu.dietwise.services.v1;

import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeAssessmentResult;
import io.smallrye.mutiny.Uni;

public interface RecipeAssessmentService {
	Uni<RecipeAssessmentResult> assessHtmlRecipe(RecipeAssessmentParam param);
}
