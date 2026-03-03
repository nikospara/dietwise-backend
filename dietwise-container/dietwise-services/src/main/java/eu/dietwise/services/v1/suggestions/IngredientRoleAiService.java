package eu.dietwise.services.v1.suggestions;

public interface IngredientRoleAiService {
	String assessIngredientRole(String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList);
}
