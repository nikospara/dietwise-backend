package eu.dietwise.services.v1.extraction.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.extraction.RecipeExtractionAiSelector;
import eu.dietwise.services.v1.extraction.RecipeExtractionAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class RecipeExtractionAiSelectorImpl implements RecipeExtractionAiSelector {
	private final RecipeExtractionAiService aiService;

	public RecipeExtractionAiSelectorImpl(RecipeExtractionAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String extractRecipeFromMarkdown(RecipeLanguage lang, String markdown) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.extractRecipeFromMarkdown(markdown);
		};
	}
}
