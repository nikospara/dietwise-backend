package eu.dietwise.services.v1.types;

import java.util.Map;
import java.util.UUID;

import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RecommendationWeight;

/**
 * A Recommendation as shown in the backoffice grid: its English name and component for scoring (immutable scoring
 * keys), its weight (informational), its effective English explanation for the LLM (published master overlaid by any
 * Staged Change), whether that explanation carries a pending change, the Working Copy version a subsequent edit must be
 * based on ({@code 0} when the explanation has no Staged Change), and the completeness of its translations for each
 * non-English language. A Recommendation is never created, deleted or renamed in the backoffice; {@code translations}
 * carries, for each non-English language, whether that translation is present, missing, or has a pending change in the
 * Working Copy.
 */
public record StagedRecommendation(
		UUID id,
		String name,
		String componentForScoring,
		RecommendationWeight weight,
		String explanationForLlm,
		boolean explanationChanged,
		long version,
		Map<RecipeLanguage, TranslationState> translations
) {
}
