package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.suggestions.TranslationLangs;
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

	/**
	 * Revert a Trigger Ingredient's staged edit, restoring its published master name and explanation and removing the
	 * Working Copy row. A no-op when no Staged Change exists. Refuses an entity that exists only in the Working Copy and
	 * has never been published (there is no master to restore, and removing the row would orphan referencing Rules).
	 *
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the entity exists only in the Working Copy (no master)
	 */
	Uni<Void> revertTriggerIngredient(ReactivePersistenceTxContext tx, UUID id, long baseVersion);

	/**
	 * For each Trigger Ingredient with at least one translation (published or staged), which languages are translated in
	 * published master and which carry a pending change in the Working Copy. Keyed by Trigger Ingredient id; sparse — an
	 * entity with no translation at all does not appear. English is the master/fallback and is never a translation language.
	 */
	Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em);

	/**
	 * The effective translation of one Trigger Ingredient for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations dialog.
	 * The returned map has an entry for every translatable language; a language with no translation has a {@code null}
	 * name and explanation and version {@code 0}.
	 */
	Uni<Map<RecipeLanguage, ReferenceDetails>> findTranslationsForEdit(ReactivePersistenceContext em, UUID id);

	/**
	 * Stage a Trigger Ingredient's name and explanation translation for one language in the Working Copy, leaving
	 * published master untouched. Staging the values already in master removes the override; reverting always removes the
	 * Working Copy row.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> stageTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert a Trigger Ingredient's staged translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion);
}
