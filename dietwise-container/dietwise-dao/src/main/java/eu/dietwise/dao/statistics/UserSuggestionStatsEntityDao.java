package eu.dietwise.dao.statistics;

import java.util.Map;
import java.util.Set;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

public interface UserSuggestionStatsEntityDao {
	/**
	 * Retrieves the total suggestion statistics for each of the given {@code Set} of {@code SuggestionTemplate} ids.
	 * <p>
	 * This is intended to serve the totals for a handful of suggestions presented to the user, so there is an enforced
	 * limit to the size of the input {@code Set}.
	 *
	 * @param em            The reactive persistence context
	 * @param applicationId The application id
	 * @param ids           The set of {@code SuggestionTemplate} ids for which to retrieve statistics
	 * @return Map suggestion id to statistics
	 */
	Uni<Map<SuggestionTemplateId, SuggestionStats>> retrieveTotalSuggestionStats(
			ReactivePersistenceContext em, String applicationId, Set<SuggestionTemplateId> ids);

	/**
	 * Retrieves the suggestion statistics for each of the given {@code Set} of {@code SuggestionTemplate} ids for the
	 * given user.
	 * <p>
	 * This is intended to serve the totals for a handful of suggestions presented to the user, so there is an enforced
	 * limit to the size of the input {@code Set}.
	 *
	 * @param em            The reactive persistence context
	 * @param applicationId The application id
	 * @param userId        The id of the user for whom to retrieve statistics
	 * @param ids           The set of {@code SuggestionTemplate} ids for which to retrieve statistics
	 * @return Map suggestion id to statistics
	 */
	Uni<Map<SuggestionTemplateId, SuggestionStats>> retrieveUserSuggestionStats(
			ReactivePersistenceContext em, String applicationId, HasUserId userId, Set<SuggestionTemplateId> ids);

	/** Increase times, return previous value. */
	Uni<Integer> increaseTimesSuggested(ReactivePersistenceTxContext tx, String applicationId, HasUserId userId, SuggestionTemplateId suggestionId);

	/** Increase times, return previous value. */
	Uni<Integer> increaseTimesAccepted(ReactivePersistenceTxContext tx, String applicationId, HasUserId userId, SuggestionTemplateId suggestionId);

	/** Decrease times, return previous value. */
	Uni<Integer> decreaseTimesAccepted(ReactivePersistenceTxContext tx, String applicationId, HasUserId userId, SuggestionTemplateId suggestionId);

	/** Increase times, return previous value. */
	Uni<Integer> increaseTimesRejected(ReactivePersistenceTxContext tx, String applicationId, HasUserId userId, SuggestionTemplateId suggestionId);

	/** Decrease times, return previous value. */
	Uni<Integer> decreaseTimesRejected(ReactivePersistenceTxContext tx, String applicationId, HasUserId userId, SuggestionTemplateId suggestionId);
}
