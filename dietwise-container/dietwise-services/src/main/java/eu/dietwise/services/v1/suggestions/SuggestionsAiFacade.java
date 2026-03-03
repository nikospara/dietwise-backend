package eu.dietwise.services.v1.suggestions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import io.smallrye.mutiny.Uni;

public interface SuggestionsAiFacade {
	Uni<Map<String, RoleOrTechnique>> retrieveAllRolesKeyedByNormalizedName(ReactivePersistenceContext em);

	String normalizeRoleName(String roleName);

	String convertRolesToMarkdownList(Collection<RoleOrTechnique> rolesOrTechniques);

	String convertInstructionsToMarkdownList(List<String> instructions);

	Uni<Map<String, TriggerIngredient>> retrieveAllTriggerIngredientsKeyedByNormalizedName(ReactivePersistenceContext em);

	String normalizeTriggerIngredientName(String triggerIngredientName);

	String convertTriggerIngredientsToMarkdownList(Collection<TriggerIngredient> triggerIngredients);

	Uni<Map<String, AlternativeIngredient>> retrieveAllAlternativesKeyedByNormalizedName(ReactivePersistenceContext em);

	/**
	 *
	 * @param availableRolesAsMarkdownList
	 * @param ingredientNameInRecipe
	 * @param instructionsAsMarkdownList
	 * @return A {@code Uni} with the outcome of the LLM assessment or empty, if the LLM could not assess; never {@code null} content
	 */
	Uni<String> assessIngredientRole(String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList);

	Uni<String> matchIngredientToTrigger(String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, RoleOrTechnique role);

	Uni<String> suggestAlternatives(String availableAlternativesAsMarkdownList, String recipeName, String ingredientNameInRecipe, String ingredientRoleOrTechnique);
}
