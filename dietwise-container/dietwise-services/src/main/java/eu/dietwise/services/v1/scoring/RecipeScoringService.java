package eu.dietwise.services.v1.scoring;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.Recipe;
import io.smallrye.mutiny.Uni;

public interface RecipeScoringService {
	Uni<ScoringRecipeAssessmentMessage> scoreRecipe(ReactivePersistenceContext em, Recipe recipe);
}
