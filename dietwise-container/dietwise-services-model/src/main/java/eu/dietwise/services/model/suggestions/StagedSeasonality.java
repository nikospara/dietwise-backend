package eu.dietwise.services.model.suggestions;

/**
 * A staged per-country seasonality in the Working Copy: the proposed in-season month range and the Working Copy version a
 * subsequent edit must be based on. Both {@code monthFrom} and {@code monthTo} null means a staged empty (the seasonality
 * is proposed to be removed).
 */
public record StagedSeasonality(Integer monthFrom, Integer monthTo, long version) {
}
