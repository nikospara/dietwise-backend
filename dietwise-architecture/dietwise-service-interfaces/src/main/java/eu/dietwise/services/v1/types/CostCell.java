package eu.dietwise.services.v1.types;

import eu.dietwise.v1.types.Cost;

/**
 * One cost cell: the effective indicative cost (published master overlaid by any Staged Change; null means no cost),
 * whether the value carries a Staged Change, and the Working Copy version a subsequent edit must be based on ({@code 0}
 * when there is no Staged Change yet).
 */
public record CostCell(Cost cost, boolean staged, long version) {
}
