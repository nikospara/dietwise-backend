package eu.dietwise.services.v1.extraction;

import eu.dietwise.services.model.RecipeExtractedFromInput;
import io.smallrye.mutiny.Uni;

public interface RecipeExtractionService {
	/**
	 * Extract a recipe from the given Markdown, or emit a failure with {@link NoRecipesDetectedException} if none found.
	 *
	 * @param markdown A Markdown text, possibly containing a recipe
	 * @return A {@code Uni} with the recipe or failure with
	 */
	Uni<RecipeExtractedFromInput> extractRecipeFromMarkdown(String markdown);
}
