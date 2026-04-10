package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiSelector;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherElAiService;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiService;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherLtAiService;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class TriggerIngredientMatcherAiSelectorImpl implements TriggerIngredientMatcherAiSelector {
	private final TriggerIngredientMatcherAiService aiServiceEn;
	private final TriggerIngredientMatcherNlAiService aiServiceNl;
	private final TriggerIngredientMatcherElAiService aiServiceEl;
	private final TriggerIngredientMatcherLtAiService aiServiceLt;

	public TriggerIngredientMatcherAiSelectorImpl(
			TriggerIngredientMatcherAiService aiServiceEn,
			TriggerIngredientMatcherNlAiService aiServiceNl,
			TriggerIngredientMatcherElAiService aiServiceEl,
			TriggerIngredientMatcherLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
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
			case EN -> aiServiceEn.matchIngredientToTrigger(
					availableTriggerIngredientsAsMarkdownList,
					ingredientNameInRecipe,
					ingredientRoleOrTechnique
			);
			case NL -> aiServiceNl.matchIngredientToTrigger(
					availableTriggerIngredientsAsMarkdownList,
					ingredientNameInRecipe,
					ingredientRoleOrTechnique
			);
			case EL -> aiServiceEl.matchIngredientToTrigger(
					availableTriggerIngredientsAsMarkdownList,
					ingredientNameInRecipe,
					ingredientRoleOrTechnique
			);
			case LT -> aiServiceLt.matchIngredientToTrigger(
					availableTriggerIngredientsAsMarkdownList,
					ingredientNameInRecipe,
					ingredientRoleOrTechnique
			);
		};
	}
}
