package eu.dietwise.services.v1.suggestions;

import eu.dietwise.v1.types.RecipeLanguage;

public interface IngredientRoleAiSelector {
	String assessIngredientRole(RecipeLanguage lang, String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList);
}
