package eu.dietwise.services.v1.suggestions.impl;

import java.util.Map;

import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.v1.model.PersonalInfo;

record RecipeSuggestionNecessaryData(
		PersonalInfo personalInfo,
		Map<String, RoleOrTechnique> roles,
		Map<String, TriggerIngredient> triggerIngredients,
		Map<String, AlternativeIngredient> alternatives,
		Map<String, RecommendationComponent> recommendations
) {
}
