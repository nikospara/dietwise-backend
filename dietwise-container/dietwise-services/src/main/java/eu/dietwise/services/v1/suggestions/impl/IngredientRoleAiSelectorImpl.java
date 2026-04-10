package eu.dietwise.services.v1.suggestions.impl;

import static java.util.Objects.requireNonNull;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.v1.suggestions.IngredientRoleAiSelector;
import eu.dietwise.services.v1.suggestions.IngredientRoleElAiService;
import eu.dietwise.services.v1.suggestions.IngredientRoleAiService;
import eu.dietwise.services.v1.suggestions.IngredientRoleLtAiService;
import eu.dietwise.services.v1.suggestions.IngredientRoleNlAiService;
import eu.dietwise.v1.types.RecipeLanguage;

@ApplicationScoped
public class IngredientRoleAiSelectorImpl implements IngredientRoleAiSelector {
	private final IngredientRoleAiService aiServiceEn;
	private final IngredientRoleNlAiService aiServiceNl;
	private final IngredientRoleElAiService aiServiceEl;
	private final IngredientRoleLtAiService aiServiceLt;

	public IngredientRoleAiSelectorImpl(
			IngredientRoleAiService aiServiceEn,
			IngredientRoleNlAiService aiServiceNl,
			IngredientRoleElAiService aiServiceEl,
			IngredientRoleLtAiService aiServiceLt
	) {
		this.aiServiceEn = aiServiceEn;
		this.aiServiceNl = aiServiceNl;
		this.aiServiceEl = aiServiceEl;
		this.aiServiceLt = aiServiceLt;
	}

	@Override
	public String assessIngredientRole(RecipeLanguage lang, String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList) {
		requireNonNull(lang, "lang must not be null");
		return switch (lang) {
			case EN -> aiServiceEn.assessIngredientRole(availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList);
			case NL -> aiServiceNl.assessIngredientRole(availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList);
			case EL -> aiServiceEl.assessIngredientRole(availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList);
			case LT -> aiServiceLt.assessIngredientRole(availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList);
		};
	}
}
