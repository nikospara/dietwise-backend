package eu.dietwise.services.v1.extraction.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.extraction.RecipeExtractionElAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionAiSelector;
import eu.dietwise.services.v1.extraction.RecipeExtractionAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionLtAiService;
import eu.dietwise.services.v1.extraction.RecipeExtractionNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class RecipeExtractionAiSelectorImpl implements RecipeExtractionAiSelector {
	private final RecipeExtractionAiService aiServiceEn;
	private final RecipeExtractionNlAiService aiServiceNl;
	private final RecipeExtractionElAiService aiServiceEl;
	private final RecipeExtractionLtAiService aiServiceLt;

	public RecipeExtractionAiSelectorImpl(
			RecipeExtractionAiService aiServiceEn,
			RecipeExtractionNlAiService aiServiceNl,
			RecipeExtractionElAiService aiServiceEl,
			RecipeExtractionLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
	}

	@Override
	public String extractRecipeFromMarkdown(RecipeLanguage lang, String markdown) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiServiceEn.extractRecipeFromMarkdown(markdown);
			case NL -> aiServiceNl.extractRecipeFromMarkdown(markdown);
			case EL -> aiServiceEl.extractRecipeFromMarkdown(markdown);
			case LT -> aiServiceLt.extractRecipeFromMarkdown(markdown);
		};
	}
}
