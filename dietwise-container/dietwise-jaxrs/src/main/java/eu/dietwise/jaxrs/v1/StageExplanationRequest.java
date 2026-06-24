package eu.dietwise.jaxrs.v1;

/**
 * Request to stage a Recommendation's English explanation for the LLM in the Working Copy.
 *
 * @param explanationForLlm The proposed explanation; may be {@code null}
 * @param baseVersion       The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
 */
public record StageExplanationRequest(String explanationForLlm, long baseVersion) {
}
