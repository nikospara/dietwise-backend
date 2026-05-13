package eu.dietwise.dao.statistics;

import java.time.LocalDateTime;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

public interface UserStatsEntityDao {
	/**
	 * Set the last launched statistic, return the previous value.
	 */
	Uni<LocalDateTime> setLastLaunched(ReactivePersistenceTxContext tx, String applicationId, UUID userId, LocalDateTime lastLaunched);

	/**
	 * Set the last seen statistic, return the previous value.
	 */
	Uni<LocalDateTime> setLastSeen(ReactivePersistenceTxContext tx, String applicationId, UUID userId, LocalDateTime lastSeen);

	/**
	 * Set the number of days launched statistic, return the previous value (not very useful, it will always be 1 less
	 * than the argument, but to keep it symmetric with the other methods).
	 */
	Uni<Integer> increaseDaysLaunched(ReactivePersistenceTxContext tx, String applicationId, UUID userId);

	/**
	 * Set the number of recipes assessed statistic, return the previous value (not very useful, it will always be 1 less
	 * than the argument, but to keep it symmetric with the other methods).
	 */
	Uni<Integer> increaseRecipesAssessed(ReactivePersistenceTxContext tx, String applicationId, UUID userId);

	/**
	 * Delete all user-level statistics for the given user, if any.
	 */
	Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId);
}
