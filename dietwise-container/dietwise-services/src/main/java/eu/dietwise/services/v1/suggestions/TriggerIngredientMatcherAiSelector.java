package eu.dietwise.services.v1.suggestions;

import eu.dietwise.v1.types.RecipeLanguage;

public interface TriggerIngredientMatcherAiSelector {
	String matchIngredientToTrigger(RecipeLanguage lang, String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, String ingredientRoleOrTechnique);
}
