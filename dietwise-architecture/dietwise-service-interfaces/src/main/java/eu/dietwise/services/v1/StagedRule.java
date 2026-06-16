package eu.dietwise.services.v1;

import eu.dietwise.v1.model.Rule;

/**
 * A Rule as shown in the backoffice grid: its effective values (published master overlaid by any Staged Change),
 * how it differs from master, and the Working Copy version a subsequent edit must be based on.
 */
public record StagedRule(Rule rule, RuleChangeState changeState, long version) {
}
