package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiSelector;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionElAiService;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiService;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionLtAiService;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class AlternativeSuggestionAiSelectorImpl implements AlternativeSuggestionAiSelector {
	private final AlternativeSuggestionAiService aiServiceEn;
	private final AlternativeSuggestionNlAiService aiServiceNl;
	private final AlternativeSuggestionElAiService aiServiceEl;
	private final AlternativeSuggestionLtAiService aiServiceLt;

	public AlternativeSuggestionAiSelectorImpl(
			AlternativeSuggestionAiService aiServiceEn,
			AlternativeSuggestionNlAiService aiServiceNl,
			AlternativeSuggestionElAiService aiServiceEl,
			AlternativeSuggestionLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
	}

	@Override
	public String suggestAlternatives(RecipeLanguage lang, String ingredientNameInRecipe, String ingredientRoleOrTechnique, String alternativesAsMarkdownList) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiServiceEn.suggestAlternatives(ingredientNameInRecipe, ingredientRoleOrTechnique, alternativesAsMarkdownList);
			case NL -> aiServiceNl.suggestAlternatives(ingredientNameInRecipe, ingredientRoleOrTechnique, alternativesAsMarkdownList);
			case EL -> aiServiceEl.suggestAlternatives(ingredientNameInRecipe, ingredientRoleOrTechnique, alternativesAsMarkdownList);
			case LT -> aiServiceLt.suggestAlternatives(ingredientNameInRecipe, ingredientRoleOrTechnique, alternativesAsMarkdownList);
		};
	}
}
