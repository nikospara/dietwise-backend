package eu.dietwise.services.v1;

import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.AlternativeIngredientForEdit;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the shared reference entities a Rule and its Suggestion Templates draw on: Trigger
 * Ingredients, Roles or Techniques and AlternativeIngredients. Reserved for users with the ADMIN role.
 */
public interface BackofficeReferenceDataService {
	/**
	 * Stage a brand-new Trigger Ingredient in the Working Copy. The name must be unique across published master and the
	 * Working Copy; an existing entry should be selected rather than duplicated.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param name The proposed Trigger Ingredient name
	 * @return The id and name of the newly staged Trigger Ingredient, selectable for a new Rule
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If a Trigger Ingredient with the same name already exists
	 */
	Uni<ReferenceOption> createTriggerIngredient(User user, String name);

	/**
	 * The effective editable details of one Trigger Ingredient (published master overlaid by any Staged Change), to
	 * pre-fill the edit dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Trigger Ingredient to edit
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Trigger Ingredient exists
	 */
	Uni<ReferenceDetails> triggerIngredientForEdit(User user, UUID id);

	/**
	 * Stage an edit to a Trigger Ingredient's name and explanation in the Working Copy. The entity is shared master
	 * data, so the edit is seen by every Rule that references it. The name must stay unique across published master and
	 * the Working Copy, excluding this entity itself.
	 *
	 * @param user             The editor; must have the ADMIN role
	 * @param id               The Trigger Ingredient being edited
	 * @param name             The proposed name
	 * @param explanationForLlm The proposed explanation for the LLM; may be {@code null}
	 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If another Trigger Ingredient already has this name
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Trigger Ingredient exists
	 */
	Uni<Void> editTriggerIngredient(User user, UUID id, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert a Trigger Ingredient's staged edit, restoring its published master name and explanation; the change is seen
	 * by every Rule that references it. A no-op when no Staged Change exists.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The Trigger Ingredient being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the entity exists only in the Working Copy (never published)
	 */
	Uni<Void> revertTriggerIngredient(User user, UUID id, long baseVersion);

	/**
	 * The effective translation of one Trigger Ingredient for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Trigger Ingredient whose translations are being edited
	 */
	Uni<Map<RecipeLanguage, ReferenceDetails>> triggerIngredientTranslationsForEdit(User user, UUID id);

	/**
	 * Stage a Trigger Ingredient's name and explanation translation for one language in the Working Copy. The entity is
	 * shared master data, so the translation is seen by every Rule that references it. Staging the value already in master
	 * removes the override.
	 *
	 * @param user             The editor; must have the ADMIN role
	 * @param id               The Trigger Ingredient being translated
	 * @param lang             The language being translated; must not be English
	 * @param name             The proposed translated name; {@code null} clears it (falls back to English)
	 * @param explanationForLlm The proposed translated explanation; {@code null} clears it (falls back to English)
	 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageTriggerIngredientTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert a Trigger Ingredient's staged translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The Trigger Ingredient whose staged translation is being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertTriggerIngredientTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion);

	/**
	 * Stage a brand-new Role or Technique in the Working Copy. The name must be unique across published master and the
	 * Working Copy; an existing entry should be selected rather than duplicated.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param name The proposed Role or Technique name
	 * @return The id and name of the newly staged Role or Technique, selectable for a new Rule
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If a Role or Technique with the same name already exists
	 */
	Uni<ReferenceOption> createRoleOrTechnique(User user, String name);

	/**
	 * The effective editable details of one Role or Technique (published master overlaid by any Staged Change), to
	 * pre-fill the edit dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Role or Technique to edit
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Role or Technique exists
	 */
	Uni<ReferenceDetails> roleOrTechniqueForEdit(User user, UUID id);

	/**
	 * Stage an edit to a Role or Technique's name and explanation in the Working Copy. The entity is shared master data,
	 * so the edit is seen by every Rule that references it. The name must stay unique across published master and the
	 * Working Copy, excluding this entity itself.
	 *
	 * @param user             The editor; must have the ADMIN role
	 * @param id               The Role or Technique being edited
	 * @param name             The proposed name
	 * @param explanationForLlm The proposed explanation for the LLM; may be {@code null}
	 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If another Role or Technique already has this name
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Role or Technique exists
	 */
	Uni<Void> editRoleOrTechnique(User user, UUID id, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert a Role or Technique's staged edit, restoring its published master name and explanation; the change is seen
	 * by every Rule that references it. A no-op when no Staged Change exists.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The Role or Technique being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the entity exists only in the Working Copy (never published)
	 */
	Uni<Void> revertRoleOrTechnique(User user, UUID id, long baseVersion);

	/**
	 * The effective translation of one Role or Technique for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Role or Technique whose translations are being edited
	 */
	Uni<Map<RecipeLanguage, ReferenceDetails>> roleOrTechniqueTranslationsForEdit(User user, UUID id);

	/**
	 * Stage a Role or Technique's name and explanation translation for one language in the Working Copy. The entity is
	 * shared master data, so the translation is seen by every Rule that references it. Staging the value already in master
	 * removes the override.
	 *
	 * @param user             The editor; must have the ADMIN role
	 * @param id               The Role or Technique being translated
	 * @param lang             The language being translated; must not be English
	 * @param name             The proposed translated name; {@code null} clears it (falls back to English)
	 * @param explanationForLlm The proposed translated explanation; {@code null} clears it (falls back to English)
	 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageRoleOrTechniqueTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert a Role or Technique's staged translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The Role or Technique whose staged translation is being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertRoleOrTechniqueTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion);

	/**
	 * Stage a brand-new AlternativeIngredient in the Working Copy with name alone (no explanation or translations yet), so
	 * it can be chosen for the Suggestion Template being added. The name must be unique across published master and the
	 * Working Copy; an existing entry should be selected rather than duplicated.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param name The proposed AlternativeIngredient name
	 * @return The id and name of the newly staged AlternativeIngredient, selectable for a Suggestion Template
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If an AlternativeIngredient with the same name already exists
	 */
	Uni<ReferenceOption> createAlternativeIngredient(User user, String name);

	/**
	 * The effective editable details of one AlternativeIngredient (published master overlaid by any Staged Change),
	 * together with its blast radius — the number of Suggestion Templates across all Rules that reference it — to pre-fill
	 * and warn within the edit dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The AlternativeIngredient to edit
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such AlternativeIngredient exists
	 */
	Uni<AlternativeIngredientForEdit> alternativeIngredientForEdit(User user, UUID id);

	/**
	 * Stage an edit to an AlternativeIngredient's name and explanation in the Working Copy. The entity is shared master
	 * data, so the edit is seen by every Suggestion Template that references it. The name must stay unique across published
	 * master and the Working Copy, excluding this entity itself.
	 *
	 * @param user             The editor; must have the ADMIN role
	 * @param id               The AlternativeIngredient being edited
	 * @param name             The proposed name
	 * @param explanationForLlm The proposed explanation for the LLM; may be {@code null}
	 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If another AlternativeIngredient already has this name
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such AlternativeIngredient exists
	 */
	Uni<Void> editAlternativeIngredient(User user, UUID id, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert an AlternativeIngredient's staged edit, restoring its published master name and explanation; the change is
	 * seen by every Suggestion Template that references it. A no-op when no Staged Change exists.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The AlternativeIngredient being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the entity exists only in the Working Copy (never published)
	 */
	Uni<Void> revertAlternativeIngredient(User user, UUID id, long baseVersion);

	/**
	 * The effective translation of one AlternativeIngredient for each non-English language (published master overlaid by
	 * any Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations
	 * dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The AlternativeIngredient whose translations are being edited
	 */
	Uni<Map<RecipeLanguage, ReferenceDetails>> alternativeIngredientTranslationsForEdit(User user, UUID id);

	/**
	 * Stage an AlternativeIngredient's name and explanation translation for one language in the Working Copy. The entity is
	 * shared master data, so the translation is seen by every Suggestion Template that references it. Staging the value
	 * already in master removes the override.
	 *
	 * @param user             The editor; must have the ADMIN role
	 * @param id               The AlternativeIngredient being translated
	 * @param lang             The language being translated; must not be English
	 * @param name             The proposed translated name; {@code null} clears it (falls back to English)
	 * @param explanationForLlm The proposed translated explanation; {@code null} clears it (falls back to English)
	 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageAlternativeIngredientTranslation(User user, UUID id, RecipeLanguage lang, String name, String explanationForLlm, long baseVersion);

	/**
	 * Revert an AlternativeIngredient's staged translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param id          The AlternativeIngredient whose staged translation is being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertAlternativeIngredientTranslation(User user, UUID id, RecipeLanguage lang, long baseVersion);
}
