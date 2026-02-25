package eu.dietwise.dao.statistics;

import java.time.LocalDateTime;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import io.smallrye.mutiny.Uni;

public interface UserStatsEntityDao {
	Uni<LocalDateTime> setLastLaunched(ReactivePersistenceTxContext tx, UUID userId, LocalDateTime lastLaunched);

	Uni<LocalDateTime> setLastSeen(ReactivePersistenceTxContext tx, UUID userId, LocalDateTime lastSeen);

	Uni<Integer> increaseDaysLaunched(ReactivePersistenceTxContext tx, UUID userId);

	Uni<Integer> increaseRecipesAssessed(ReactivePersistenceTxContext tx, UUID userId);
}
