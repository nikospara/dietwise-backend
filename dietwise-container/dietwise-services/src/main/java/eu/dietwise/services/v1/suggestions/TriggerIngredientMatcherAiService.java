package eu.dietwise.services.v1.suggestions;

public interface TriggerIngredientMatcherAiService {
	String matchIngredientToTrigger(String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, String ingredientRoleOrTechnique);
}
