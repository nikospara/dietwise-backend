package eu.dietwise.services.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.services.v1.types.StagedRule;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Rule master data. Reserved for users with the ADMIN role.
 */
public interface BackofficeRulesService {
	/**
	 * List every Rule for the backoffice grid: published master overlaid by the Working Copy, with each Rule's
	 * change state and Working Copy version.
	 *
	 * @param user The user requesting the rules; must have the ADMIN role
	 * @return All Rules, master overlaid by any Staged Change
	 */
	Uni<List<StagedRule>> listRules(User user);

	/**
	 * The Suggestion Templates of one Rule for the backoffice panel: published master overlaid by the Working Copy, each
	 * with the AlternativeIngredient it suggests, its effective English swap notes, which of those fields carry a pending
	 * change, and the Working Copy version a subsequent edit must be based on. Ordered by their position within the Rule.
	 *
	 * @param user   The editor; must have the ADMIN role
	 * @param ruleId The Rule whose Suggestion Templates are listed
	 * @return The Rule's Suggestion Templates, empty when it has none
	 */
	Uni<List<StagedSuggestionTemplate>> listSuggestionTemplates(User user, RuleId ruleId);

	/**
	 * Stage one English field ({@code restriction}, {@code equivalence} or {@code technique_notes}) of a Suggestion
	 * Template in the Working Copy, leaving published master and recipe assessment untouched.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param templateId  The Suggestion Template whose field is being staged
	 * @param field       The field being staged
	 * @param value       The proposed value; may be {@code null}
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @return The template's new Working Copy version
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such published Suggestion Template exists
	 */
	Uni<Long> stageSuggestionTemplateField(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, String value, long baseVersion);

	/**
	 * Revert one staged English field of a Suggestion Template, restoring the published master value. When no override
	 * remains the template's Working Copy row is removed. A no-op when the template has no Staged Change.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param templateId  The Suggestion Template whose field is being reverted
	 * @param field       The field being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertSuggestionTemplateField(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, long baseVersion);

	/**
	 * Stage a Suggestion Template's active state in the Working Copy, leaving published master and recipe assessment
	 * untouched. Deactivating a published template, so recipe assessment stops offering that alternative, or reactivating
	 * a deactivated one, is a Staged Change like any field; staging the value the template already has in master removes
	 * the override.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param templateId  The Suggestion Template whose active state is being staged
	 * @param active      The proposed active state
	 * @param baseVersion The Working Copy version the change is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such published Suggestion Template exists
	 */
	Uni<Void> setSuggestionTemplateActive(User user, SuggestionTemplateId templateId, boolean active, long baseVersion);

	/**
	 * The effective translation of one field of a Suggestion Template for each non-English language (published master
	 * overlaid by any Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the
	 * per-field translations dialog. The returned map has an entry for every translatable language; a language with no
	 * translation has a {@code null} text and version {@code 0}.
	 *
	 * @param user       The editor; must have the ADMIN role
	 * @param templateId The Suggestion Template whose translations are being edited
	 * @param field      The field whose translations are being edited
	 */
	Uni<Map<RecipeLanguage, VersionedText>> templateFieldTranslationsForEdit(User user, SuggestionTemplateId templateId, SuggestionTemplateField field);

	/**
	 * Stage one field of a Suggestion Template's translation for one language in the Working Copy, leaving published master
	 * and recipe assessment untouched. Staging the value already in master, or clearing a field with no master translation,
	 * removes the override.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param templateId  The Suggestion Template being translated
	 * @param field       The field being translated
	 * @param lang        The language being translated; must not be English
	 * @param value       The proposed translated value; {@code null} clears it (falls back to English)
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageTemplateFieldTranslation(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, RecipeLanguage lang, String value, long baseVersion);

	/**
	 * Revert one staged field of a Suggestion Template's translation for one language, restoring the published master
	 * translation. When no override remains the template+language Working Copy row is removed.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param templateId  The Suggestion Template whose staged translation is being reverted
	 * @param field       The field being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertTemplateFieldTranslation(User user, SuggestionTemplateId templateId, SuggestionTemplateField field, RecipeLanguage lang, long baseVersion);

	/**
	 * Stage a new rationale for a Rule in the Working Copy, leaving published master and recipe assessment untouched.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose rationale is being staged
	 * @param rationale   The proposed rationale; may be {@code null}
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @return The Rule's new Working Copy version
	 */
	Uni<Long> stageRationale(User user, RuleId ruleId, String rationale, long baseVersion);

	/**
	 * Revert a Rule's staged rationale in the Working Copy, restoring the published master value. When the Rule has no
	 * other Staged Change left, its Working Copy row is removed entirely.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose staged rationale is being reverted
	 * @param baseVersion The Working Copy version the revert is based on
	 */
	Uni<Void> revertRationale(User user, RuleId ruleId, long baseVersion);

	/**
	 * Stage a Rule's active state in the Working Copy, leaving published master and recipe assessment untouched.
	 * Deactivating an applied Rule, or activating a deactivated one, is a Staged Change like any other; staging the
	 * value the Rule already has in master removes the override.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose active state is being staged
	 * @param active      The proposed active state
	 * @param baseVersion The Working Copy version the change is based on ({@code 0} when no Staged Change exists yet)
	 */
	Uni<Void> setActive(User user, RuleId ruleId, boolean active, long baseVersion);

	/**
	 * Stage a brand-new Rule in the Working Copy from existing reference data, choosing its business key. The new Rule
	 * starts active with no rationale and shows as a new row until published.
	 *
	 * @param user                The editor; must have the ADMIN role
	 * @param recommendationId    The chosen Recommendation
	 * @param triggerIngredientId The chosen Trigger Ingredient
	 * @param roleOrTechniqueId   The chosen Role or Technique, or {@code null} for none
	 * @return The id of the newly staged Rule
	 * @throws eu.dietwise.common.dao.DuplicateBusinessKeyException If a Rule with the same business key already exists
	 *                                                              in published master or the Working Copy
	 */
	Uni<RuleId> createRule(User user, UUID recommendationId, UUID triggerIngredientId, UUID roleOrTechniqueId);

	/**
	 * Discard an unpublished new Rule from the Working Copy, removing it from the grid. Only a Rule that exists solely
	 * in the Working Copy can be discarded; a published Rule is deactivated instead.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Working-Copy-only Rule to discard
	 * @param baseVersion The Working Copy version the discard is based on
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the Rule is published rather than a Working-Copy-only Rule
	 */
	Uni<Void> discardNewRule(User user, RuleId ruleId, long baseVersion);

	/**
	 * The reference data an editor chooses from when creating a new Rule.
	 *
	 * @param user The editor; must have the ADMIN role
	 */
	Uni<NewRuleOptions> newRuleOptions(User user);

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
	 * The effective editable details of one Trigger Ingredient (published master overlaid by any Staged Change), to
	 * pre-fill the edit dialog.
	 *
	 * @param user The editor; must have the ADMIN role
	 * @param id   The Trigger Ingredient to edit
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Trigger Ingredient exists
	 */
	Uni<ReferenceDetails> triggerIngredientForEdit(User user, UUID id);

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
	 * The effective rationale translation of one Rule for each non-English language (published master overlaid by any
	 * Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the translations
	 * dialog. The returned map has an entry for every translatable language; a language with no translation has a {@code
	 * null} text and version {@code 0}.
	 *
	 * @param user   The editor; must have the ADMIN role
	 * @param ruleId The Rule whose rationale translations are being edited
	 */
	Uni<Map<RecipeLanguage, VersionedText>> rationaleTranslationsForEdit(User user, RuleId ruleId);

	/**
	 * Stage a Rule's rationale translation for one language in the Working Copy, leaving published master and recipe
	 * assessment untouched. Staging the value already in master removes the override.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose translation is being staged
	 * @param lang        The language being translated; must not be English
	 * @param rationale   The proposed translated rationale; {@code null} clears the translation (falls back to English)
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> stageRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, String rationale, long baseVersion);

	/**
	 * Revert a Rule's staged rationale translation for one language, restoring the published master translation and
	 * removing the Working Copy row.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param ruleId      The Rule whose staged translation is being reverted
	 * @param lang        The language being reverted; must not be English
	 * @param baseVersion The Working Copy version the revert is based on
	 * @throws IllegalArgumentException If {@code lang} is English, which is the master value rather than a translation
	 */
	Uni<Void> revertRationaleTranslation(User user, RuleId ruleId, RecipeLanguage lang, long baseVersion);

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
}
