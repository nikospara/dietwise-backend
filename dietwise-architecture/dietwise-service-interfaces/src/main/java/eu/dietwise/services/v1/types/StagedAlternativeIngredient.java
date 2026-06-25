package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;

/**
 * One Alternative Ingredient row of the substitution-value grid: its id, effective English name (published master
 * overlaid by any Staged Change), whether a published master row exists behind it, the Working Copy version a subsequent
 * name/explanation edit must be based on ({@code 0} when there is no Staged Change yet), the completeness of its
 * translations for each non-English language, and its links to the grid's Recommendation columns.
 * <p>
 * The links are given as two sets, both restricted to the grid's ENCOURAGED columns: {@code linkedRecommendationIds} are
 * the Recommendations linked in published master, and {@code stagedRecommendationIds} are the Recommendations whose link
 * carries a pending change in the Working Copy. A column's effective presence is the master link toggled by a staged
 * change: a staged id absent from master is a staged addition, a staged id present in master is a staged removal. A row
 * with {@code published == false} exists only in the Working Copy and may be discarded.
 */
public record StagedAlternativeIngredient(
		UUID id,
		String name,
		boolean published,
		long version,
		Map<RecipeLanguage, TranslationState> translations,
		Set<UUID> linkedRecommendationIds,
		Set<UUID> stagedRecommendationIds
) {
}
