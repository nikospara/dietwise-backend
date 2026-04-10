package eu.dietwise.services.v1.filtering.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.filtering.RecipeFilterElAiService;
import eu.dietwise.services.v1.filtering.RecipeFilterAiSelector;
import eu.dietwise.services.v1.filtering.RecipeFilterAiService;
import eu.dietwise.services.v1.filtering.RecipeFilterLtAiService;
import eu.dietwise.services.v1.filtering.RecipeFilterNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class RecipeFilterAiSelectorImpl implements RecipeFilterAiSelector {
	private final RecipeFilterAiService aiServiceEn;
	private final RecipeFilterNlAiService aiServiceNl;
	private final RecipeFilterElAiService aiServiceEl;
	private final RecipeFilterLtAiService aiServiceLt;

	public RecipeFilterAiSelectorImpl(
			RecipeFilterAiService aiServiceEn,
			RecipeFilterNlAiService aiServiceNl,
			RecipeFilterElAiService aiServiceEl,
			RecipeFilterLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
	}

	@Override
	public String filterRecipeBlock(RecipeLanguage lang, String block) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiServiceEn.filterRecipeBlock(block);
			case NL -> aiServiceNl.filterRecipeBlock(block);
			case EL -> aiServiceEl.filterRecipeBlock(block);
			case LT -> aiServiceLt.filterRecipeBlock(block);
		};
	}
}
