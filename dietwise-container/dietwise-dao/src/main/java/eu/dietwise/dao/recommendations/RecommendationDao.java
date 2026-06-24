package eu.dietwise.dao.recommendations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.services.model.recommendations.BackofficeRecommendation;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.TranslationLangs;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
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

	Uni<List<RecommendationComponent>> listAllRecommendationsForScoring(ReactivePersistenceContext em, RecipeLanguage lang);

	/**
	 * Every Recommendation as an id + English name, ordered by name, for selection in the backoffice.
	 */
	Uni<List<ReferenceOption>> listOptions(ReactivePersistenceContext em);

	/**
	 * Every Recommendation's master fields for the backoffice grid (id, English name, component for scoring, weight,
	 * English explanation for the LLM), ordered by name.
	 */
	Uni<List<BackofficeRecommendation>> listForBackoffice(ReactivePersistenceContext em);

	/**
	 * For each Recommendation that has at least one translation, which non-English languages it is translated into,
	 * split into published master translations ({@code present}) and pending Working Copy translations ({@code staged}).
	 * Used to derive the per-language completeness shown on the grid.
	 */
	Uni<Map<UUID, TranslationLangs>> findTranslationLangs(ReactivePersistenceContext em);
}
