package eu.dietwise.services.v1.types;

/**
 * One seasonality cell: the effective in-season month range (published master overlaid by any Staged Change; both months
 * null means no seasonality), whether the value carries a Staged Change, and the Working Copy version a subsequent edit
 * must be based on ({@code 0} when there is no Staged Change yet).
 */
public record SeasonalityCell(Integer monthFrom, Integer monthTo, boolean staged, long version) {
}
