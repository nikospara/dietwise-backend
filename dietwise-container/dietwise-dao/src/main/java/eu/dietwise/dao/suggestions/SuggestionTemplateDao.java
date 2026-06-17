package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.services.model.suggestions.FieldTranslationLangs;
import eu.dietwise.services.model.suggestions.NewSuggestionTemplate;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.v1.model.SuggestionTemplate;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface SuggestionTemplateDao {
	/**
	 * The published Suggestion Templates of one Rule, each with the AlternativeIngredient it suggests and its English
	 * {@code restriction}, {@code equivalence} and {@code techniqueNotes}, ordered by {@code alternative_order}. A Rule
	 * with no templates yields an empty list.
	 */
	Uni<List<SuggestionTemplate>> findByRule(ReactivePersistenceContext em, UUID ruleId);

	/**
	 * The Working Copy overlays for one Rule's Suggestion Templates, keyed by template id. Returned sparsely: only a
	 * template that carries a Staged Change has an entry. Each value is a full snapshot of the staged row's English
	 * fields plus its version.
	 */
	Uni<Map<UUID, StagedSuggestionTemplateOverlay>> findStagedOverlayByRule(ReactivePersistenceContext em, UUID ruleId);

	/**
	 * The Working-Copy-only Suggestion Templates of one Rule — those an editor added but has not published, with no
	 * published master row — each with the AlternativeIngredient it suggests and its proposed English fields, ordered by
	 * {@code alternative_order}. A Rule with no added templates yields an empty list.
	 */
	Uni<List<NewSuggestionTemplate>> findNewByRule(ReactivePersistenceContext em, UUID ruleId);

	/**
	 * The id of the Suggestion Template a Rule already has for a given AlternativeIngredient, looked up across published
	 * master union the Working Copy and ignoring the template's active state, or empty when the Rule has none. Lets the
	 * add flow surface the existing template (offering Reactivate when it is deactivated) instead of creating a duplicate.
	 */
	Uni<Optional<UUID>> findTemplateIdByRuleAndAlternative(ReactivePersistenceContext em, UUID ruleId, UUID alternativeIngredientId);

	/**
	 * Stage a brand-new Suggestion Template for a Rule in the Working Copy, suggesting an existing AlternativeIngredient.
	 * The new template gets a generated id, starts active, has no English fields, and is positioned after the Rule's
	 * existing templates ({@code alternative_order} = max over master union Working Copy + 1). Leaves published master and
	 * recipe assessment untouched. The caller must have already checked that the Rule has no template for this
	 * AlternativeIngredient.
	 *
	 * @return The id of the newly staged template
	 */
	Uni<UUID> addTemplate(ReactivePersistenceTxContext tx, UUID ruleId, UUID alternativeIngredientId);

	/**
	 * Discard an unpublished new Suggestion Template from the Working Copy, removing it from the panel. Only a template
	 * that exists solely in the Working Copy can be discarded; a published template is deactivated instead. A no-op when
	 * the template's Working Copy row is already gone.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the template is published rather than Working-Copy-only
	 */
	Uni<Void> discardTemplate(ReactivePersistenceTxContext tx, UUID templateId, long baseVersion);

	/**
	 * The published active state of one Rule's Suggestion Templates, keyed by template id. Lets the backoffice panel show
	 * the effective active state (master overlaid by the Working Copy) and tell whether a deactivation or activation is a
	 * Staged Change. A Rule with no templates yields an empty map.
	 */
	Uni<Map<UUID, Boolean>> findActiveByRule(ReactivePersistenceContext em, UUID ruleId);

	/**
	 * The ids of the Rules that have at least one Suggestion Template with a Staged Change in the Working Copy, whether an
	 * English field or a translation. Used to light a Rule's Suggestions affordance without loading its templates.
	 */
	Uni<Set<UUID>> findRuleIdsWithStagedTemplates(ReactivePersistenceContext em);

	/**
	 * The per-field, per-language translation completeness of one Rule's Suggestion Templates, keyed by template id. For
	 * each template, each translatable field reports which languages have a published master translation ({@code present})
	 * and which have a pending change in the Working Copy ({@code staged}). Drives the three independent chip-sets per
	 * template card. A template with no translation activity at all has no entry.
	 */
	Uni<Map<UUID, FieldTranslationLangs>> findFieldTranslationLangsByRule(ReactivePersistenceContext em, UUID ruleId);

	/**
	 * The effective translation of one field of a Suggestion Template for each non-English language (published master
	 * overlaid by any Staged Change) and the Working Copy version a subsequent edit must be based on, to pre-fill the
	 * per-field translations dialog. The returned map has an entry for every translatable language; a language with no
	 * translation has a {@code null} text and version {@code 0}. The version is that of the whole template+language
	 * Working Copy row, shared by the template's three fields.
	 */
	Uni<Map<RecipeLanguage, VersionedText>> findFieldTranslationsForEdit(ReactivePersistenceContext em, UUID templateId, SuggestionTemplateField field);

	/**
	 * Stage one field of a Suggestion Template's translation for one language in the Working Copy, leaving published master
	 * and recipe assessment untouched. On the first touch a whole-row snapshot of the master translation is seeded, so the
	 * template's other translated fields keep falling back as before; staging the value already in master, or clearing a
	 * field that has no master translation, leaves no override. When no override remains the template+language Working Copy
	 * row is removed.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> stageFieldTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, SuggestionTemplateField field, String value, long baseVersion);

	/**
	 * Revert one staged field of a Suggestion Template's translation for one language, restoring the published master
	 * translation of that field. When no override remains the template+language Working Copy row is removed. A no-op when
	 * the template+language has no Staged Change.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertFieldTranslation(ReactivePersistenceTxContext tx, UUID templateId, RecipeLanguage lang, SuggestionTemplateField field, long baseVersion);

	/**
	 * Stage one English field of a Suggestion Template in the Working Copy, leaving published master untouched. On the
	 * first touch a whole-row snapshot of master is seeded; subsequent edits bump the field and the version. Returns the
	 * template's new Working Copy version.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such published Suggestion Template exists
	 */
	Uni<Long> stageField(ReactivePersistenceTxContext tx, UUID templateId, SuggestionTemplateField field, String value, long baseVersion);

	/**
	 * Stage a Suggestion Template's active state in the Working Copy, leaving published master and recipe assessment
	 * untouched. On the first touch a whole-row snapshot of master is seeded; deactivating a published template, or
	 * activating a deactivated one, is a Staged Change like any English field. Staging the value the template already has
	 * in master removes the override, and when no override remains the template's Working Copy row is removed.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such published Suggestion Template exists
	 */
	Uni<Void> setActive(ReactivePersistenceTxContext tx, UUID templateId, boolean active, long baseVersion);

	/**
	 * Revert one staged English field of a Suggestion Template to its published master value. When no override remains
	 * the template's Working Copy row is removed. A no-op when the template has no Staged Change.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertField(ReactivePersistenceTxContext tx, UUID templateId, SuggestionTemplateField field, long baseVersion);
}
