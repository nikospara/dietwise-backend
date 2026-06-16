package eu.dietwise.dao.suggestions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
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
	 * Revert a Rule's staged rationale, restoring the published master value. As rationale is currently the only
	 * staged field, reverting it leaves the Working Copy row with no remaining override and the row is removed.
	 * Reverting a Rule that has no Staged Change is a no-op.
	 *
	 * @param ruleId      The Rule whose staged rationale is being reverted
	 * @param baseVersion The Working Copy version the caller based the revert on
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> revertRationale(ReactivePersistenceTxContext tx, UUID ruleId, long baseVersion);
}
