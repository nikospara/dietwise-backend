package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.services.model.suggestions.RuleBusinessKey;
import eu.dietwise.services.model.suggestions.RuleReferences;
import eu.dietwise.services.model.suggestions.StagedNewRule;
import eu.dietwise.services.model.suggestions.StagedRuleOverlay;
import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

public interface RuleDao {
	Uni<List<Rule>> findByTriggerIngredient(ReactivePersistenceContext em, HasTriggerIngredientId triggerIngredientId, RecipeLanguage lang);

	Uni<List<Rule>> findAll(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * The Staged Changes held in the Working Copy, keyed by Rule id. Sparse: only Rules with a staged change appear.
	 */
	Uni<Map<UUID, StagedRuleOverlay>> findStagedOverlay(ReactivePersistenceContext em);

	/**
	 * Stage a new rationale for a Rule in the Working Copy, leaving the published master untouched.
	 *
	 * @param ruleId      The Rule whose rationale is being staged
	 * @param rationale   The proposed rationale; may be {@code null}
	 * @param baseVersion The Working Copy version the caller based the edit on ({@code 0} when no Staged Change exists yet)
	 * @return The Rule's new Working Copy version
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Rule exists in master to stage against
	 */
	Uni<Long> stageRationale(ReactivePersistenceTxContext tx, UUID ruleId, String rationale, long baseVersion);

	/**
	 * Revert a Rule's staged rationale, restoring the published master value. If the rationale was the Rule's only
	 * staged field, its Working Copy row is removed; if other fields (e.g. a staged Deactivate) still differ from
	 * master, the row is kept with only the rationale reset. Reverting a Rule that has no Staged Change is a no-op.
	 *
	 * @param ruleId      The Rule whose staged rationale is being reverted
	 * @param baseVersion The Working Copy version the caller based the revert on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertRationale(ReactivePersistenceTxContext tx, UUID ruleId, long baseVersion);

	/**
	 * Stage a Rule's active state in the Working Copy, leaving the published master untouched. Staging the value the
	 * Rule already has in master removes the override; if no other field still differs, the Working Copy row collapses.
	 *
	 * @param ruleId      The Rule whose active state is being staged
	 * @param active      The proposed active state
	 * @param baseVersion The Working Copy version the caller based the change on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If no such Rule exists in master to stage against
	 */
	Uni<Void> setActive(ReactivePersistenceTxContext tx, UUID ruleId, boolean active, long baseVersion);

	/**
	 * Stage a brand-new Rule in the Working Copy from existing reference data. The references are stored as raw ids;
	 * they are not validated here (the caller validates the business key). The new row starts active with no rationale.
	 *
	 * @param recommendationId    The chosen Recommendation
	 * @param triggerIngredientId The chosen Trigger Ingredient
	 * @param roleOrTechniqueId   The chosen Role or Technique, or {@code null} for none
	 * @return The generated Working Copy id of the new Rule
	 */
	Uni<UUID> createRule(ReactivePersistenceTxContext tx, UUID recommendationId, UUID triggerIngredientId, UUID roleOrTechniqueId);

	/**
	 * Discard a Rule that exists only in the Working Copy (no published master), removing its Working Copy row.
	 * Discarding a Rule that has no Working Copy row is a no-op. A Rule that is published (has a master) cannot be
	 * discarded — it is deactivated instead.
	 *
	 * @param ruleId      The Working-Copy-only Rule to discard
	 * @param baseVersion The Working Copy version the caller based the discard on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 * @throws eu.dietwise.common.dao.EntityNotFoundException If the Rule is published rather than a Working-Copy-only Rule
	 */
	Uni<Void> discardNewRule(ReactivePersistenceTxContext tx, UUID ruleId, long baseVersion);

	/**
	 * The Rules that exist only in the Working Copy (no published master), with their references resolved to English
	 * names and their Working Copy version. Sparse: only Working-Copy-only Rules appear.
	 */
	Uni<List<StagedNewRule>> findNewRules(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * Every Rule business key currently in use across published master and the Working Copy, for uniqueness checks.
	 */
	Uni<Set<RuleBusinessKey>> findBusinessKeys(ReactivePersistenceContext em);

	/**
	 * The reference-data ids each Rule points at, keyed by Rule id, across published master and the Working Copy. Used
	 * to overlay effective Trigger Ingredient / Role or Technique names onto the grid and to flag every Rule that
	 * references a shared entity with a pending edit.
	 */
	Uni<Map<UUID, RuleReferences>> findReferenceIds(ReactivePersistenceContext em);
}
