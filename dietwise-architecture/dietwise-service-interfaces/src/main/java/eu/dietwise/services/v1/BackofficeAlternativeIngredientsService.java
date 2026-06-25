package eu.dietwise.services.v1;

import java.util.UUID;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.AlternativeIngredientRecommendationGrid;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the substitution value of Alternative Ingredients: the grid of which ENCOURAGED
 * Recommendations each Alternative Ingredient provides, and the discarding of Working-Copy-only Alternative Ingredients.
 * The grid's cell toggles and the row's name, explanation and translation edits are staged; published master and recipe
 * assessment are left untouched until a separate publish step. Reserved for users with the ADMIN role.
 */
public interface BackofficeAlternativeIngredientsService {
	/**
	 * The whole substitution-value grid: the ENCOURAGED Recommendation columns and, per Alternative Ingredient, its
	 * effective name, translation completeness and links to those columns (published master overlaid by any Staged
	 * Change).
	 *
	 * @param user The editor; must have the ADMIN role
	 */
	Uni<AlternativeIngredientRecommendationGrid> recommendationGrid(User user);

	/**
	 * Stage a single Alternative-Ingredient-to-Recommendation link to the given effective presence in the Working Copy,
	 * leaving published master untouched. Staging the presence master already has collapses the Staged Change. An
	 * unversioned toggle: a binary link to an absolute target presence carries no lost-update hazard, so no base version
	 * is taken and no stale-version check is performed.
	 *
	 * @param user                    The editor; must have the ADMIN role
	 * @param alternativeIngredientId The Alternative Ingredient whose link is being toggled
	 * @param recommendationId        The Recommendation the link points to
	 * @param present                 The target effective presence of the link
	 */
	Uni<Void> toggleRecommendation(User user, UUID alternativeIngredientId, UUID recommendationId, boolean present);

	/**
	 * Discard a Working-Copy-only Alternative Ingredient, removing its Working Copy row, staged translations and staged
	 * links. Refused for an Alternative Ingredient that has a published master baseline, and for one still referenced by
	 * a Suggestion Template.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Alternative Ingredient to discard
	 * @throws eu.dietwise.common.dao.EntityInUseException If the Alternative Ingredient is published or is still referenced
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Alternative Ingredient exists
	 */
	Uni<Void> discardAlternativeIngredient(User user, UUID id);
}
