package eu.dietwise.services.v1.types;

import java.util.List;

import eu.dietwise.common.types.ReferenceOption;

/**
 * The reference data an editor chooses from when creating a new Rule: the available Recommendations, Trigger
 * Ingredients and Roles or Techniques (a Role or Technique is optional on a Rule).
 */
public record NewRuleOptions(
		List<ReferenceOption> recommendations,
		List<ReferenceOption> triggerIngredients,
		List<ReferenceOption> rolesOrTechniques
) {
}
