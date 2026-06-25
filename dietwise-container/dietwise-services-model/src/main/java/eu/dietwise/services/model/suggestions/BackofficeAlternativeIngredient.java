package eu.dietwise.services.model.suggestions;

import java.util.UUID;

/**
 * One Alternative Ingredient row for the backoffice grid: its id, effective English name (published master overlaid by
 * any Staged Change), whether a published master row exists, and the Working Copy version a subsequent name/explanation
 * edit must be based on ({@code 0} when there is no Staged Change yet). A row with {@code published == false} exists only
 * in the Working Copy and may be discarded.
 */
public record BackofficeAlternativeIngredient(UUID id, String name, boolean published, long version) {
}
