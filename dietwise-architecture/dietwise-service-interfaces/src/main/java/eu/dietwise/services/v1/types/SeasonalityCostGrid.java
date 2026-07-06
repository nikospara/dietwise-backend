package eu.dietwise.services.v1.types;

import java.util.List;

import eu.dietwise.v1.types.Country;

/**
 * The whole Seasonality &amp; Cost grid: the {@code countries} that form the per-country columns (in display order) and one
 * {@code rows} entry per Alternative Ingredient (sorted by name), each carrying its effective and staged per-country
 * seasonality and cost.
 */
public record SeasonalityCostGrid(List<Country> countries, List<SeasonalityCostRow> rows) {
}
