package eu.dietwise.jaxrs.v1;

/**
 * Request to stage a Recommendation's translation for one language in the Working Copy. The three fields share one
 * version and are staged together.
 *
 * @param name                The proposed translated name; may be {@code null} to clear it (falls back to English)
 * @param componentForScoring The proposed translated component for scoring; may be {@code null} to clear it
 * @param explanationForLlm   The proposed translated explanation for the LLM; may be {@code null} to clear it
 * @param baseVersion         The Working Copy version the edit is based on ({@code 0} when no Staged Change exists yet)
 */
public record EditRecommendationTranslationRequest(String name, String componentForScoring, String explanationForLlm, long baseVersion) {
}
