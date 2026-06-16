package eu.dietwise.services.model.suggestions;

/**
 * The staged fields of a Rule held in the Working Copy that can differ from published master, together with the
 * Working Copy version used for the optimistic concurrency check. Returned sparsely: only Rules that carry a
 * Staged Change have an overlay.
 */
public record StagedRuleOverlay(String rationale, long version) {
}
