package eu.dietwise.services.model.suggestions;

import eu.dietwise.v1.model.SuggestionTemplate;

/**
 * A Suggestion Template that exists only in the Working Copy (no published master), with its AlternativeIngredient
 * already resolved to a name, together with the Working Copy version a subsequent edit or discard must be based on. A
 * Working-Copy-only template is always active until published.
 */
public record NewSuggestionTemplate(SuggestionTemplate template, long version) {
}
