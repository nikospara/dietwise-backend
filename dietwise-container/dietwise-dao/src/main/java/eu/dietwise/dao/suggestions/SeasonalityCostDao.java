package eu.dietwise.dao.suggestions;

import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.services.model.suggestions.AlternativeIngredientCountryKey;
import eu.dietwise.services.model.suggestions.StagedCost;
import eu.dietwise.services.model.suggestions.StagedSeasonality;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.Seasonality;
import io.smallrye.mutiny.Uni;

/**
 * Reads and staged writes of the per-country seasonality and cost of Alternative Ingredients, for the backoffice
 * Seasonality &amp; Cost grid. Writes go to the Working Copy mirror tables, leaving published master untouched.
 */
public interface SeasonalityCostDao {
	/**
	 * The published master seasonality of every Alternative Ingredient and country. Keyed by Alternative Ingredient id
	 * and country; sparse — a cell with no master seasonality does not appear.
	 */
	Uni<Map<AlternativeIngredientCountryKey, Seasonality>> findMasterSeasonality(ReactivePersistenceContext em);

	/**
	 * The staged seasonality in the Working Copy of every Alternative Ingredient and country, with the Working Copy
	 * version a subsequent edit must be based on. Keyed by Alternative Ingredient id and country; sparse — a cell with no
	 * Staged Change does not appear. A staged value with both months null is a staged empty (the seasonality is proposed
	 * to be removed).
	 */
	Uni<Map<AlternativeIngredientCountryKey, StagedSeasonality>> findStagedSeasonality(ReactivePersistenceContext em);

	/**
	 * The published master cost of every Alternative Ingredient and country. Keyed by Alternative Ingredient id and
	 * country; sparse — a cell with no master cost does not appear.
	 */
	Uni<Map<AlternativeIngredientCountryKey, Cost>> findMasterCost(ReactivePersistenceContext em);

	/**
	 * The staged cost in the Working Copy of every Alternative Ingredient and country, with the Working Copy version a
	 * subsequent edit must be based on. Keyed by Alternative Ingredient id and country; sparse — a cell with no Staged
	 * Change does not appear. A staged value with a null cost is a staged empty (the cost is proposed to be removed).
	 */
	Uni<Map<AlternativeIngredientCountryKey, StagedCost>> findStagedCost(ReactivePersistenceContext em);

	/**
	 * Stage a per-country seasonality for an Alternative Ingredient in the Working Copy, leaving published master
	 * untouched. Both months null stages an empty (the seasonality is proposed to be removed). Staging the value master
	 * already has removes the override; when the staged value equals master the Working Copy row collapses.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> stageSeasonality(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo, long baseVersion);

	/**
	 * Stage a per-country cost for an Alternative Ingredient in the Working Copy, leaving published master untouched. A
	 * null cost stages an empty (the cost is proposed to be removed). Staging the value master already has removes the
	 * override; when the staged value equals master the Working Copy row collapses.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws eu.dietwise.common.dao.StaleVersionException If {@code baseVersion} no longer matches the current version
	 */
	Uni<Void> stageCost(ReactivePersistenceTxContext tx, UUID alternativeIngredientId, Country country, Cost cost, long baseVersion);
}
