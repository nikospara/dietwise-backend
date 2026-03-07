package eu.dietwise.dao.recommendations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

public interface RecommendationDao {
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age, BiologicalGender gender);

	/**
	 * Find recommendation values when the user has specified only their biological gender.
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, BiologicalGender gender);

	/**
	 * Find recommendation values when the user has specified only their age (date of birth actually).
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em, int age);

	/**
	 * Find recommendation values when the user has specified no personal information.
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendations(ReactivePersistenceContext em);

	Uni<List<RecommendationComponent>> listAllRecommendationsForScoring(ReactivePersistenceContext em);
}
