package eu.dietwise.services.v1.extraction;

import eu.dietwise.v1.model.Recipe;
import io.smallrye.mutiny.Uni;

public interface RecipeExtractionService {
	Uni<Recipe> extractRecipeFromMarkdown(String markdown);

	Uni<Recipe> extractRecipeFromHtml(String html);
}
