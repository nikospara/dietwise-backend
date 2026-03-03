package eu.dietwise.services.v1.suggestions.impl;

import java.util.Map;

import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;

record RecipeSuggestionNecessaryData(
		Map<String, RoleOrTechnique> roles,
		Map<String, TriggerIngredient> triggerIngredients,
		Map<String, AlternativeIngredient> alternatives) {
}
