package eu.dietwise.services.model.recommendations;

import java.util.UUID;

import eu.dietwise.v1.types.TypeOfRecommendation;

/**
 * A Recommendation's master fields as shown in the backoffice grid: its id, its English name and component for scoring,
 * its type of recommendation, its English explanation for the LLM and its English human friendly display. A carrier between the DAO and
 * the service layer; the grid's per-language translation completeness is carried separately.
 */
public record BackofficeRecommendation(
		UUID id,
		String name,
		String componentForScoring,
		TypeOfRecommendation typeOfRecommendation,
		String explanationForLlm,
		String humanFriendlyDisplay
) {
}
