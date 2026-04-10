package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.IngredientRoleAiSelector;
import eu.dietwise.services.v1.suggestions.IngredientRoleAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class IngredientRoleAiSelectorImpl implements IngredientRoleAiSelector {
	private final IngredientRoleAiService aiService;

	public IngredientRoleAiSelectorImpl(IngredientRoleAiService aiService) {
		this.aiService = aiService;
	}

	@Override
	public String assessIngredientRole(RecipeLanguage lang, String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiService.assessIngredientRole(availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList);
		};
	}
}
