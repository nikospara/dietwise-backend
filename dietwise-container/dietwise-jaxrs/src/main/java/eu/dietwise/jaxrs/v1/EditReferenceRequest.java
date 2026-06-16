package eu.dietwise.jaxrs.v1;

/**
 * Request to stage an edit to a shared reference entity (a Trigger Ingredient or a Role or Technique) in the Working
 * Copy.
 *
 * @param name             The proposed name
 * @param explanationForLlm The proposed explanation for the LLM; may be {@code null}
 * @param baseVersion      The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
 */
public record EditReferenceRequest(String name, String explanationForLlm, long baseVersion) {
}
