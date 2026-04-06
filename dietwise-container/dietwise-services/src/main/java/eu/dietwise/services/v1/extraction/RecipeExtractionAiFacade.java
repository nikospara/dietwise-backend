package eu.dietwise.services.v1.extraction;

import io.smallrye.mutiny.Uni;

public interface RecipeExtractionAiFacade {
	Uni<String> extractRecipeFromMarkdown(String markdown);
}
