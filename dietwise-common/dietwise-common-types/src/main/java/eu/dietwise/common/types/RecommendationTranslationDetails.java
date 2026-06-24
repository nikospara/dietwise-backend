package eu.dietwise.common.types;

/**
 * The editable translation of a Recommendation in one language: its translated name, component for scoring and
 * explanation for the LLM, and the Working Copy version a subsequent edit must be based on ({@code 0} when no Staged
 * Change exists yet). The values are effective: published master overlaid by any Staged Change. The three fields share a
 * single version, so they are staged and reverted together. A field is {@code null} when that part of the translation is
 * absent and falls back to English.
 */
public record RecommendationTranslationDetails(String name, String componentForScoring, String explanationForLlm, long version) {
}
