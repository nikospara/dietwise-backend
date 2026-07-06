package eu.dietwise.services.v1;

import java.util.UUID;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.SeasonalityCostGrid;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice editing of the per-country seasonality and cost of Alternative Ingredients. All operations require an admin
 * user; writes stage into the Working Copy, leaving published master untouched.
 */
public interface BackofficeSeasonalityCostService {
	/**
	 * The whole Seasonality &amp; Cost grid: every Alternative Ingredient (published master overlaid by the Working Copy)
	 * with its effective and staged per-country seasonality and cost.
	 */
	Uni<SeasonalityCostGrid> grid(User user);

	/**
	 * Stage a per-country seasonality for an Alternative Ingredient. Both months null stages an empty (the seasonality is
	 * proposed to be removed); otherwise both months must be present and between 1 and 12.
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 * @throws IllegalArgumentException If exactly one month is set, or a month is outside 1..12
	 */
	Uni<Void> stageSeasonality(User user, UUID alternativeIngredientId, Country country, Integer monthFrom, Integer monthTo, long baseVersion);

	/**
	 * Stage a per-country cost for an Alternative Ingredient. A null cost stages an empty (the cost is proposed to be
	 * removed).
	 *
	 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
	 */
	Uni<Void> stageCost(User user, UUID alternativeIngredientId, Country country, Cost cost, long baseVersion);
}
