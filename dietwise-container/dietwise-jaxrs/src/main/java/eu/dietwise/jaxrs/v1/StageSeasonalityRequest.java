package eu.dietwise.jaxrs.v1;

/**
 * A request to stage a per-country seasonality: the proposed in-season month range (both null to clear it) and the
 * Working Copy version the edit is based on.
 */
public record StageSeasonalityRequest(Integer monthFrom, Integer monthTo, long baseVersion) {
}
