package eu.dietwise.services.v1.suggestions;

import java.util.UUID;

import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import io.smallrye.mutiny.Uni;

public interface RecipeSuggestionsService {
	Uni<SuggestionsRecipeAssessmentMessage> makeSuggestions(HasUserId hasUserId, Recipe recipe);

	/**
	 * Try to increase the times each suggestion was offered. Will log any failures silently.
	 */
	Uni<Void> increaseTimesSuggested(UUID correlationId, String applicationId, HasUserId hasUserId, HasSuggestionTemplateIds suggestions);
}
