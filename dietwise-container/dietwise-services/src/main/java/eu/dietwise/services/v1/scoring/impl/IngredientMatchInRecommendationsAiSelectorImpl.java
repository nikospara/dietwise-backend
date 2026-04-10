package eu.dietwise.services.v1.scoring.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiSelector;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsElAiService;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiService;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsLtAiService;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class IngredientMatchInRecommendationsAiSelectorImpl implements IngredientMatchInRecommendationsAiSelector {
	private final IngredientMatchInRecommendationsAiService aiServiceEn;
	private final IngredientMatchInRecommendationsNlAiService aiServiceNl;
	private final IngredientMatchInRecommendationsElAiService aiServiceEl;
	private final IngredientMatchInRecommendationsLtAiService aiServiceLt;

	public IngredientMatchInRecommendationsAiSelectorImpl(
			IngredientMatchInRecommendationsAiService aiServiceEn,
			IngredientMatchInRecommendationsNlAiService aiServiceNl,
			IngredientMatchInRecommendationsElAiService aiServiceEl,
			IngredientMatchInRecommendationsLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
	}

	@Override
	public String matchIngredientsWithRecommendations(RecipeLanguage lang, String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiServiceEn.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredientNameInRecipe);
			case NL -> aiServiceNl.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredientNameInRecipe);
			case EL -> aiServiceEl.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredientNameInRecipe);
			case LT -> aiServiceLt.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredientNameInRecipe);
		};
	}
}
