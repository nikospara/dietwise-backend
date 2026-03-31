package eu.dietwise.services.v1.extraction;

import eu.dietwise.services.model.RecipeExtractedFromInput;
import io.smallrye.mutiny.Uni;

public interface MarkdownRecipeExtractionService {
	/**
	 * Try to extract a recipe from the given Markdown.
	 *
	 * @param markdown A Markdown text, possibly containing a recipe
	 * @return A {@code Uni} with the recipe or failure with
	 */
	Uni<RecipeExtractedFromInput> extractRecipeFromMarkdown(String markdown);
}
