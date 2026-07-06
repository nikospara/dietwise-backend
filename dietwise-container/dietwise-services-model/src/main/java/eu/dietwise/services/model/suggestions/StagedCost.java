package eu.dietwise.services.model.suggestions;

import eu.dietwise.v1.types.Cost;

/**
 * A staged per-country cost in the Working Copy: the proposed indicative cost and the Working Copy version a subsequent
 * edit must be based on. A {@code null} {@code cost} means a staged empty (the cost is proposed to be removed).
 */
public record StagedCost(Cost cost, long version) {
}
