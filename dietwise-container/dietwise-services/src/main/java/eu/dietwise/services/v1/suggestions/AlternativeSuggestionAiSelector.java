package eu.dietwise.services.v1.suggestions;

import eu.dietwise.v1.types.RecipeLanguage;

public interface AlternativeSuggestionAiSelector {
	String suggestAlternatives(RecipeLanguage lang, String ingredientNameInRecipe, String ingredientRoleOrTechnique, String alternativesAsMarkdownList);
}
