package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.UUID;

import eu.dietwise.v1.types.Country;

/**
 * One Alternative Ingredient row of the Seasonality &amp; Cost grid: its id, effective English name (published master
 * overlaid by any Staged Change), whether a published master row exists, and its per-country seasonality and cost cells
 * keyed by country.
 */
public record SeasonalityCostRow(
		UUID id,
		String name,
		boolean published,
		Map<Country, SeasonalityCell> seasonality,
		Map<Country, CostCell> cost
) {
}
