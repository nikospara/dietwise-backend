package eu.dietwise.services.v1.extraction;

import eu.dietwise.services.model.RecipeExtractedFromInput;
import io.smallrye.mutiny.Uni;

public interface RecipeExtractionService {
	Uni<RecipeExtractedFromInput> extractRecipeFromMarkdown(String markdown);

	Uni<RecipeExtractedFromInput> extractRecipeFromHtml(String html);
}
