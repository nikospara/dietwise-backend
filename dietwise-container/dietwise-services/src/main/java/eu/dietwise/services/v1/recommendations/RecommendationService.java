package eu.dietwise.services.v1.recommendations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

/**
 * The recommendations of the national nutrition guidelines, as the rest of the system needs them. The guidelines change
 * only when the backoffice publishes them, which the application only sees by being restarted, so what is read here is
 * kept in memory for the lifetime of the application.
 */
public interface RecommendationService {
	/**
	 * The recommendation weights that apply to a user, as precise as their profile allows: the weights of their age
	 * group and biological gender when the profile gives both, averaged over whichever of the two it leaves out otherwise.
	 *
	 * @param personalInfo The profile of the user, may be {@code null} or partially filled in
	 */
	Uni<Map<Recommendation, BigDecimal>> findRecommendationWeights(ReactivePersistenceContext em, PersonalInfo personalInfo);

	/**
	 * Every recommendation component the system scores a recipe against, named in the given language.
	 */
	Uni<List<RecommendationComponent>> listComponentsForScoring(ReactivePersistenceContext em, RecipeLanguage lang);
}
