package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface TriggerIngredientDao {
	Uni<List<TriggerIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * The selectable Trigger Ingredients for the backoffice: published master overlaid by the Working Copy (mirror
	 * wins), including Working-Copy-only entries, as id + English name, ordered by name.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);

	/**
	 * Stage a brand-new Trigger Ingredient in the Working Copy with the given name and no explanation. Uniqueness of the
	 * name across master and the Working Copy is the caller's responsibility; the Working Copy carries a unique-name
	 * constraint as a backstop.
	 *
	 * @return The generated Working Copy id of the new Trigger Ingredient
	 */
	Uni<UUID> createTriggerIngredient(ReactivePersistenceTxContext tx, String name);

	/**
	 * The Trigger Ingredients that carry a Staged Change in the Working Copy, as id to English name. Sparse: only
	 * entities with a Working Copy row appear (an edited existing entity or one created in the Working Copy). Used to
	 * overlay the effective name onto the grid and to flag the affected cells.
	 */
	Uni<Map<UUID, String>> findStagedNames(ReactivePersistenceContext em);

	/**
	 * The effective editable details of one Trigger Ingredient: published master overlaid by any Staged Change, with the
	 * Working Copy version a subsequent edit must be based on ({@code 0} when no Staged Change exists yet).
	 *
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Trigger Ingredient exists
	 */
	Uni<ReferenceDetails> findEditableById(ReactivePersistenceContext em, UUID id);

	/**
	 * Stage an edit to a Trigger Ingredient's name and explanation in the Working Copy, leaving published master
	 * untouched. The edit is shared: every Rule referencing this entity sees it. Staging the values the entity already
	 * has in master removes the override; if no field still differs, the Working Copy row collapses. Name uniqueness
	 * across master and the Working Copy is the caller's responsibility; the Working Copy carries a unique-name backstop.
	 *
	 * @param baseVersion The Working Copy version the caller based the edit on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Trigger Ingredient exists
	 */
	Uni<Void> editTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion);
}
