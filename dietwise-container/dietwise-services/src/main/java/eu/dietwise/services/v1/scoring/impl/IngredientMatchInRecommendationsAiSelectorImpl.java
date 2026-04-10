package eu.dietwise.services.v1.scoring.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiSelector;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class IngredientMatchInRecommendationsAiSelectorImpl implements IngredientMatchInRecommendationsAiSelector {
	private final IngredientMatchInRecommendationsAiService aiService;

	public IngredientMatchInRecommendationsAiSelectorImpl(IngredientMatchInRecommendationsAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String matchIngredientsWithRecommendations(RecipeLanguage lang, String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredientNameInRecipe);
		};
	}
}
