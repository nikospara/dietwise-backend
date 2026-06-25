package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import java.util.Set;

import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.BackofficeAlternativeIngredient;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface AlternativeIngredientDao {
	Uni<List<AlternativeIngredient>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * Every Alternative Ingredient for the backoffice grid: published master overlaid by the Working Copy (mirror wins),
	 * including Working-Copy-only entries, as id, effective English name, whether a published master row exists, and the
	 * Working Copy version a subsequent edit must be based on ({@code 0} when no Staged Change exists yet). Sorted by name.
	 */
	Uni<List<BackofficeAlternativeIngredient>> listForBackoffice(ReactivePersistenceContext em);

	/**
	 * The published master scoring-component links: for each Alternative Ingredient, the ids of the Recommendations it is
	 * linked to in {@code DW_ALTERNATIVE_INGREDIENT_COMPONENTS_FOR_SCORING}. Sparse — an ingredient with no master link
	 * does not appear.
	 */
	Uni<Map<UUID, Set<UUID>>> findMasterRecommendationLinks(ReactivePersistenceContext em);

	/**
	 * The staged scoring-component links in the Working Copy: for each Alternative Ingredient, the ids of the
	 * Recommendations whose link carries a Staged Change, mapped to the staged effective presence ({@code true} = staged
	 * addition, {@code false} = staged removal). Sparse — an ingredient with no staged link change does not appear.
	 */
	Uni<Map<UUID, Map<UUID, Boolean>>> findStagedRecommendationLinks(ReactivePersistenceContext em);

	/**
	 * Stage a single Alternative-Ingredient-to-Recommendation scoring-component link to the given effective presence in
	 * the Working Copy, leaving published master untouched. Staging the presence master already has collapses the Working
	 * Copy row. An unversioned toggle: a binary link to an absolute target presence carries no lost-update hazard, so no
	 * base version is taken and no stale-version check is performed.
	 */
	Uni<Void> toggleRecommendationLink(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, UUID recommendationId, boolean present);

	/**
	 * Discard a Working-Copy-only Alternative Ingredient: remove its Working Copy row, its staged translations and its
	 * staged scoring-component links. Touches only Working Copy data; the caller is responsible for refusing a published
	 * ingredient or one still referenced by a Suggestion Template.
	 */
	Uni<Void> discardAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id);

	/**
	 * The selectable AlternativeIngredients for the backoffice: published master overlaid by the Working Copy (mirror
	 * wins), including Working-Copy-only entries, as id and English name, sorted by name. Backs the filtering combobox an
	 * editor uses to add a Suggestion Template to a Rule.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);

	/**
	 * Stage a brand-new AlternativeIngredient in the Working Copy with the given name and no explanation. Uniqueness of
	 * the name across master and the Working Copy is the caller's responsibility; the Working Copy carries a unique-name
	 * constraint as a backstop.
	 *
	 * @return The generated Working Copy id of the new AlternativeIngredient
	 */
	Uni<UUID> createAlternativeIngredient(ReactivePersistenceTxContext tx, String name);

	/**
	 * The AlternativeIngredients that carry a Staged Change in the Working Copy, as id to English name. Sparse: only
	 * entities with a Working Copy row appear (an edited existing entity or one created in the Working Copy). Used to
	 * overlay the effective name onto the Suggestion Templates that reference them.
	 */
	Uni<Map<UUID, String>> findStagedNames(ReactivePersistenceContext em);

	/**
	 * The effective editable details of one AlternativeIngredient: published master overlaid by any Staged Change, with
	 * the Working Copy version a subsequent edit must be based on ({@code 0} when no Staged Change exists yet).
	 *
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such AlternativeIngredient exists
	 */
	Uni<ReferenceDetails> findEditableById(ReactivePersistenceContext em, UUID id);

	/**
	 * Stage an edit to an AlternativeIngredient's name and explanation in the Working Copy, leaving published master
	 * untouched. The edit is shared: every Suggestion Template referencing this entity sees it. Staging the values the
	 * entity already has in master removes the override; if no field still differs, the Working Copy row collapses. Name
	 * uniqueness across master and the Working Copy is the caller's responsibility; the Working Copy carries a
	 * unique-name backstop.
	 *
	 * @param baseVersion The Working Copy version the caller based the edit on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such AlternativeIngredient exists
	 */
	Uni<Void> editAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert an AlternativeIngredient's staged edit, restoring its published master name and explanation and removing the
	 * Working Copy row. A no-op when no Staged Change exists. Refuses an entity that exists only in the Working Copy and
	 * has never been published (there is no master to restore, and removing the row would orphan referencing Suggestion
	 * Templates).
	 *
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the entity exists only in the Working Copy (no master)
	 */
	Uni<Void> revertAlternativeIngredient(ReactivePersistenceTxContext tx, UUID id, long baseVersion);

	/**
	 * For each AlternativeIngredient with at least one translation (published or staged), which languages are translated
	 * in published master and which carry a pending change in the Working Copy. Keyed by AlternativeIngredient id;
	 * sparse — an entity with no translation at all does not appear. English is the master/fallback and is never a
	 * translation language.
	 */
	Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em);

	/**
	 * The effective translation of one AlternativeIngredient for each non-English language (published master overlaid by
	 * any Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations
	 * dialog. The returned map has an entry for every translatable language; a language with no translation has a {@code
	 * null} name and explanation and version {@code 0}.
	 */
	Uni<Map<RecipeLanguage, ReferenceDetails>> findTranslationsForEdit(ReactivePersistenceContext em, UUID id);

	/**
	 * Stage an AlternativeIngredient's name and explanation translation for one language in the Working Copy, leaving
	 * published master untouched. Staging the values already in master removes the override; reverting always removes the
	 * Working Copy row.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> stageTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert an AlternativeIngredient's staged translation for one language, restoring the published master translation
	 * and removing the Working Copy row.
	 *
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertTranslation(ReactivePersistenceTxContext tx, UUID id, RecipeLanguage lang, long baseVersion);
}
