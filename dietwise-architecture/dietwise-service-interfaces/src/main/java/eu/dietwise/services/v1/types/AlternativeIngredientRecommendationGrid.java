package eu.dietwise.services.v1.types;

import java.util.List;

/**
 * The whole substitution-value grid: the ENCOURAGED Recommendation {@code columns} (in display order) and one
 * {@code ingredients} row per Alternative Ingredient (sorted by name), each carrying its links to those columns. A cell
 * is the crossing of a row and a column; its effective presence and pending-change state are derived from the row's
 * master and staged link sets.
 */
public record AlternativeIngredientRecommendationGrid(
		List<RecommendationColumn> columns,
		List<StagedAlternativeIngredient> ingredients
) {
}
