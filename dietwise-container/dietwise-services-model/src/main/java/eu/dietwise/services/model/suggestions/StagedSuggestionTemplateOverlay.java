package eu.dietwise.services.model.suggestions;

/**
 * The staged English text fields of a Suggestion Template held in the Working Copy that can differ from published
 * master, together with the Working Copy version used for the optimistic concurrency check. Returned sparsely, keyed by
 * template id: only templates that carry a Staged Change have an overlay. Each value is a full snapshot of the staged
 * row, so an unchanged field holds the same value as master.
 */
public record StagedSuggestionTemplateOverlay(String restriction, String equivalence, String techniqueNotes, long version) {
}
