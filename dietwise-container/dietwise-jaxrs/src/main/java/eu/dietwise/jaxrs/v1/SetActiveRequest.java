package eu.dietwise.jaxrs.v1;

/**
 * Request to stage the active state of a Rule or a Suggestion Template in the Working Copy.
 *
 * @param active      The proposed active state
 * @param baseVersion The Working Copy version the change is based on ({@code 0} when no Staged Change exists yet)
 */
public record SetActiveRequest(boolean active, long baseVersion) {
}
