package eu.dietwise.jaxrs.v1;

/**
 * Request to stage one English field of a Suggestion Template in the Working Copy.
 *
 * @param value       The proposed value; may be {@code null}
 * @param baseVersion The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
 */
public record StageTemplateFieldRequest(String value, long baseVersion) {
}
