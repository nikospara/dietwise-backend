package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.services.model.suggestions.StagedSuggestionTemplateOverlay;
import eu.dietwise.v1.model.SuggestionTemplate;
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
	 * The ids of the Rules that have at least one Suggestion Template with a Staged Change in the Working Copy. Used to
	 * light a Rule's Suggestions affordance without loading its templates.
	 */
	Uni<Set<UUID>> findRuleIdsWithStagedTemplates(ReactivePersistenceContext em);

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
	 * Revert one staged English field of a Suggestion Template to its published master value. When no override remains
	 * the template's Working Copy row is removed. A no-op when the template has no Staged Change.
	 *
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertField(ReactivePersistenceTxContext tx, UUID templateId, SuggestionTemplateField field, long baseVersion);
}
