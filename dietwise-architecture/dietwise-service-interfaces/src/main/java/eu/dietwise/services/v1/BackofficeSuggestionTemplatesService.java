package eu.dietwise.services.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.AddedTemplate;
import eu.dietwise.services.v1.types.StagedSuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Suggestion Templates of a Rule. Reserved for users with the ADMIN role.
 */
public interface BackofficeSuggestionTemplatesService {
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
	 * The published AlternativeIngredients an editor can choose from when adding a Suggestion Template to a Rule, as id
	 * and English name, sorted by name.
	 *
	 * @param user The editor; must have the ADMIN role
	 */
	Uni<List<ReferenceOption>> alternativeIngredientOptions(User user);

	/**
	 * Add a Suggestion Template to a Rule for an existing AlternativeIngredient, staged in the Working Copy and leaving
	 * published master and recipe assessment untouched. The new template starts active with no English fields, positioned
	 * after the Rule's existing templates. The business key (rule, alternative ingredient) is unique regardless of active
	 * state: if the Rule already has a template for the alternative, no duplicate is created and the existing one is
	 * returned instead (so the caller can offer to reactivate it when it is deactivated).
	 *
	 * @param user                  The editor; must have the ADMIN role
	 * @param ruleId                The Rule the template is added to
	 * @param alternativeIngredientId The chosen existing AlternativeIngredient
	 * @return The id of the template covering the alternative, and whether it was newly created
	 */
	Uni<AddedTemplate> addSuggestionTemplate(User user, RuleId ruleId, UUID alternativeIngredientId);

	/**
	 * Discard an unpublished new Suggestion Template from the Working Copy, removing it from the panel. Only a template
	 * that exists solely in the Working Copy can be discarded; a published template is deactivated instead.
	 *
	 * @param user        The editor; must have the ADMIN role
	 * @param templateId  The Working-Copy-only template to discard
	 * @param baseVersion The Working Copy version the discard is based on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the template is published rather than Working-Copy-only
	 */
	Uni<Void> discardSuggestionTemplate(User user, SuggestionTemplateId templateId, long baseVersion);

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
}
