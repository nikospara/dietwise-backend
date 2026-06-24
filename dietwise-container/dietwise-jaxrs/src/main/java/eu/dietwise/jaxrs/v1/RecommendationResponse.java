package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import eu.dietwise.services.v1.types.StagedRecommendation;
import eu.dietwise.services.v1.types.TranslationState;
import eu.dietwise.v1.types.RecipeLanguage;

/**
 * A single Recommendation as shown in the backoffice grid: its English name and component for scoring, its weight
 * ({@code ENCOURAGED} or {@code LIMITED}), its English explanation for the LLM (may be {@code null}) and, per non-English
 * language, the completeness state of its translation (language name to state name).
 */
public record RecommendationResponse(
		String id,
		String name,
		String componentForScoring,
		String weight,
		String explanationForLlm,
		Map<String, String> translations
) {
	public static RecommendationResponse from(StagedRecommendation recommendation) {
		return new RecommendationResponse(
				recommendation.id().toString(),
				recommendation.name(),
				recommendation.componentForScoring(),
				recommendation.weight().name(),
				recommendation.explanationForLlm(),
				toStateNames(recommendation.translations()));
	}

	private static Map<String, String> toStateNames(Map<RecipeLanguage, TranslationState> states) {
		return states.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().name(), entry -> entry.getValue().name()));
	}

	public static List<RecommendationResponse> fromAll(List<StagedRecommendation> recommendations) {
		return recommendations.stream().map(RecommendationResponse::from).toList();
	}
}
