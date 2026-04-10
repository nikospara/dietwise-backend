package eu.dietwise.services.v1.scoring;

import eu.dietwise.v1.types.RecipeLanguage;

public interface IngredientMatchInRecommendationsAiSelector {
	String matchIngredientsWithRecommendations(RecipeLanguage lang, String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe);
}
