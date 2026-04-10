package eu.dietwise.services.v1.filtering.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.filtering.RecipeFilterAiSelector;
import eu.dietwise.services.v1.filtering.RecipeFilterAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class RecipeFilterAiSelectorImpl implements RecipeFilterAiSelector {
	private final RecipeFilterAiService aiService;

	public RecipeFilterAiSelectorImpl(RecipeFilterAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String filterRecipeBlock(RecipeLanguage lang, String block) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.filterRecipeBlock(block);
		};
	}
}
