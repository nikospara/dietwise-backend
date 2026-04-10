package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.FindBestRuleAiSelector;
import eu.dietwise.services.v1.suggestions.FindBestRuleAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class FindBestRuleAiSelectorImpl implements FindBestRuleAiSelector {
	private final FindBestRuleAiService aiService;

	public FindBestRuleAiSelectorImpl(FindBestRuleAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String findBestRule(
			RecipeLanguage lang,
			String ingredientNameInRecipe,
			String ingredientRoleOrTechnique,
			String triggerIngredient,
			String dietaryComponentsMarkdownList,
			String filteredRulesMarkdownList
	) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.findBestRule(
					ingredientNameInRecipe,
					ingredientRoleOrTechnique,
					triggerIngredient,
					dietaryComponentsMarkdownList,
					filteredRulesMarkdownList
			);
		};
	}
}
