package eu.dietwise.services.model.recommendations;

import java.util.UUID;

import eu.dietwise.v1.types.RecommendationWeight;

/**
 * A Recommendation's master fields as shown in the backoffice grid: its id, its English name and component for scoring,
 * its weight and its English explanation for the LLM. A carrier between the DAO and the service layer; the grid's
 * per-language translation completeness is carried separately.
 */
public record BackofficeRecommendation(
		UUID id,
		String name,
		String componentForScoring,
		RecommendationWeight weight,
		String explanationForLlm
) {
}
