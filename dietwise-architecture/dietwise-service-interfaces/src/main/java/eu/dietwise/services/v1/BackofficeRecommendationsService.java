package eu.dietwise.services.v1;

import java.util.List;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.types.StagedRecommendation;
import io.smallrye.mutiny.Uni;

/**
 * Backoffice operations on the Recommendation master data. A Recommendation cannot be created, deleted, deactivated or
 * renamed; only its English explanation for the LLM and the translations of its fields are editable. Reserved for users
 * with the ADMIN role.
 */
public interface BackofficeRecommendationsService {
	/**
	 * List every Recommendation for the backoffice grid: its English name and component for scoring, its weight, its
	 * effective English explanation for the LLM, and the completeness of its translations, ordered by name.
	 *
	 * @param user The user requesting the recommendations; must have the ADMIN role
	 * @return All Recommendations, ordered by name
	 */
	Uni<List<StagedRecommendation>> listRecommendations(User user);
}
