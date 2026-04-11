package eu.dietwise.services.v1.suggestions;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface SuggestionsAiFacade {
	Uni<Map<String, RoleOrTechnique>> retrieveAllRolesKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang);

	String convertRolesToMarkdownList(Collection<RoleOrTechnique> rolesOrTechniques);

	String convertInstructionsToMarkdownList(List<String> instructions);

	Uni<Map<String, TriggerIngredient>> retrieveAllTriggerIngredientsKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang);

	String convertTriggerIngredientsToMarkdownList(Collection<TriggerIngredient> triggerIngredients);

	Uni<Map<String, AlternativeIngredient>> retrieveAllAlternativesKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang);

	Uni<Map<String, RecommendationComponent>> retrieveAllRecommendationsKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang);

	String convertRecommendationsToMarkdownList(Collection<RecommendationComponent> recommendations);

	/**
	 *
	 * @param availableRolesAsMarkdownList
	 * @param ingredientNameInRecipe
	 * @param instructionsAsMarkdownList
	 * @return A {@code Uni} with the outcome of the LLM assessment or empty, if the LLM could not assess; never {@code null} content
	 */
	Uni<String> assessIngredientRole(RecipeLanguage lang, String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList);

	Uni<String> matchIngredientToTrigger(RecipeLanguage lang, String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, RoleOrTechnique role);

	Uni<Set<String>> matchIngredientsWithRecommendations(RecipeLanguage lang, String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe);

	Uni<String> findBestRule(RecipeLanguage lang, String ingredientNameInRecipe, RoleOrTechnique role, TriggerIngredient triggerIngredient, Collection<RecommendationComponent> dietaryComponents, Collection<Rule> filteredRules);

	Uni<String> suggestAlternatives(RecipeLanguage lang, String ingredientNameInRecipe, RoleOrTechnique role, List<Suggestion> alternatives);
}
