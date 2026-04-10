package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiSelector;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class AlternativeSuggestionAiSelectorImpl implements AlternativeSuggestionAiSelector {
	private final AlternativeSuggestionAiService aiService;

	public AlternativeSuggestionAiSelectorImpl(AlternativeSuggestionAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String suggestAlternatives(RecipeLanguage lang, String ingredientNameInRecipe, String ingredientRoleOrTechnique, String alternativesAsMarkdownList) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.suggestAlternatives(ingredientNameInRecipe, ingredientRoleOrTechnique, alternativesAsMarkdownList);
		};
	}
}
