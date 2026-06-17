package eu.dietwise.services.model.suggestions;

/**
 * The staged fields of a Suggestion Template held in the Working Copy that can differ from published master — its
 * English text fields and its active state — together with the Working Copy version used for the optimistic concurrency
 * check. Returned sparsely, keyed by template id: only templates that carry a Staged Change have an overlay. Each value
 * is a full snapshot of the staged row, so an unchanged field holds the same value as master.
 */
public record StagedSuggestionTemplateOverlay(String restriction, String equivalence, String techniqueNotes, boolean active, long version) {
}
