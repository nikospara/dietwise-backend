package eu.dietwise.services.v1.extraction;

import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface RecipeExtractionAiFacade {
	Uni<String> extractRecipeFromMarkdown(RecipeLanguage lang, String markdown);
}
