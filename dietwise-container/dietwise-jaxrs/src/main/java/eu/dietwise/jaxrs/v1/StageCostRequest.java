package eu.dietwise.jaxrs.v1;

/**
 * A request to stage a per-country cost: the proposed indicative cost name (LO, MED or HI; null or blank to clear it) and
 * the Working Copy version the edit is based on.
 */
public record StageCostRequest(String cost, long baseVersion) {
}
