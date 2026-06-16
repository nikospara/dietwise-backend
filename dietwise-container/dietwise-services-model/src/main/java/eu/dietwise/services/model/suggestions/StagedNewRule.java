package eu.dietwise.services.model.suggestions;

import eu.dietwise.v1.model.Rule;

/**
 * A Rule that exists only in the Working Copy (no published master), with its references already resolved to names,
 * together with the Working Copy version a subsequent edit must be based on.
 */
public record StagedNewRule(Rule rule, long version) {
}
