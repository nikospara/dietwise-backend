package eu.dietwise.services.v1.suggestions;

import eu.dietwise.v1.types.RecipeLanguage;

public interface FindBestRuleAiSelector {
	String findBestRule(
			RecipeLanguage lang,
			String ingredientNameInRecipe,
			String ingredientRoleOrTechnique,
			String triggerIngredient,
			String dietaryComponentsMarkdownList,
			String filteredRulesMarkdownList
	);
}
