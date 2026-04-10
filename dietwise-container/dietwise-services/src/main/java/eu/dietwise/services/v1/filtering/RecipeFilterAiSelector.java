package eu.dietwise.services.v1.filtering;

import eu.dietwise.v1.types.RecipeLanguage;

public interface RecipeFilterAiSelector {
	String filterRecipeBlock(RecipeLanguage lang, String block);
}
