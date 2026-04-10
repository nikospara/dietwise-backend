package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.FindBestRuleAiSelector;
import eu.dietwise.services.v1.suggestions.FindBestRuleElAiService;
import eu.dietwise.services.v1.suggestions.FindBestRuleAiService;
import eu.dietwise.services.v1.suggestions.FindBestRuleLtAiService;
import eu.dietwise.services.v1.suggestions.FindBestRuleNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class FindBestRuleAiSelectorImpl implements FindBestRuleAiSelector {
	private final FindBestRuleAiService aiServiceEn;
	private final FindBestRuleNlAiService aiServiceNl;
	private final FindBestRuleElAiService aiServiceEl;
	private final FindBestRuleLtAiService aiServiceLt;

	public FindBestRuleAiSelectorImpl(
			FindBestRuleAiService aiServiceEn,
			FindBestRuleNlAiService aiServiceNl,
			FindBestRuleElAiService aiServiceEl,
			FindBestRuleLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
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
			case EN -> aiServiceEn.findBestRule(
					ingredientNameInRecipe,
					ingredientRoleOrTechnique,
					triggerIngredient,
					dietaryComponentsMarkdownList,
					filteredRulesMarkdownList
			);
			case NL -> aiServiceNl.findBestRule(
					ingredientNameInRecipe,
					ingredientRoleOrTechnique,
					triggerIngredient,
					dietaryComponentsMarkdownList,
					filteredRulesMarkdownList
			);
			case EL -> aiServiceEl.findBestRule(
					ingredientNameInRecipe,
					ingredientRoleOrTechnique,
					triggerIngredient,
					dietaryComponentsMarkdownList,
					filteredRulesMarkdownList
			);
			case LT -> aiServiceLt.findBestRule(
					ingredientNameInRecipe,
					ingredientRoleOrTechnique,
					triggerIngredient,
					dietaryComponentsMarkdownList,
					filteredRulesMarkdownList
			);
		};
	}
}
