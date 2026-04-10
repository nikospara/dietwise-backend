package eu.dietwise.services.v1.extraction;

import eu.dietwise.v1.types.RecipeLanguage;

public interface RecipeExtractionAiSelector {
	String extractRecipeFromMarkdown(RecipeLanguage lang, String markdown);
}
