package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiSelector;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class TriggerIngredientMatcherAiSelectorImpl implements TriggerIngredientMatcherAiSelector {
	private final TriggerIngredientMatcherAiService aiService;

	public TriggerIngredientMatcherAiSelectorImpl(TriggerIngredientMatcherAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String matchIngredientToTrigger(
			RecipeLanguage lang,
			String availableTriggerIngredientsAsMarkdownList,
			String ingredientNameInRecipe,
			String ingredientRoleOrTechnique
	) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.matchIngredientToTrigger(availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, ingredientRoleOrTechnique);
		};
	}
}
