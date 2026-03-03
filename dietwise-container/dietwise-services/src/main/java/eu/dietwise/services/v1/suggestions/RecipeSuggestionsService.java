package eu.dietwise.services.v1.suggestions;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.Recipe;
import io.smallrye.mutiny.Uni;

public interface RecipeSuggestionsService {
	Uni<SuggestionsRecipeAssessmentMessage> makeSuggestions(User user, Recipe recipe);
}
