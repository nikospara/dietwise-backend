package eu.dietwise.dao.statistics;

import java.time.LocalDateTime;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

public interface UserRecipeStatsEntityDao {
	/** Increase times assessed and update the stored recipe details, return new value. */
	Uni<Integer> increaseTimesAssessed(
			ReactivePersistenceTxContext tx,
			String applicationId,
			UUID userId,
			String recipeUrl,
			String recipeName,
			LocalDateTime lastAssessed
	);

	/** Delete all per-recipe statistics for the given user, if any. */
	Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId);
}
