package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.StagedRecommendation;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * A single Recommendation as shown in the backoffice grid: its English name and component for scoring, its weight
 * ({@code ENCOURAGED} or {@code LIMITED}), its effective English explanation for the LLM (published master overlaid by
 * any Staged Change, may be {@code null}), whether that explanation carries a pending change, the Working Copy version a
 * subsequent edit must be based on ({@code 0} when it has no Staged Change) and, per non-English language, the
 * completeness state of its translation (language name to state name).
 */
public record RecommendationResponse(
		String id,
		String name,
		String componentForScoring,
		String weight,
		String explanationForLlm,
		boolean explanationChanged,
		long version,
		Map<String, String> translations
) {
	public static RecommendationResponse from(StagedRecommendation recommendation) {
		return new RecommendationResponse(
				recommendation.id().toString(),
				recommendation.name(),
				recommendation.componentForScoring(),
				recommendation.weight().name(),
				recommendation.explanationForLlm(),
				recommendation.explanationChanged(),
				recommendation.version(),
				toStateNames(recommendation.translations()));
	}

	private static Map<String, String> toStateNames(Map<RecipeLanguage, TranslationState> states) {
		return states.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name()));
	}

	public static List<RecommendationResponse> fromAll(List<StagedRecommendation> recommendations) {
		return recommendations.stream().map(RecommendationResponse::from).toList();
	}
}
