package eu.dietwise.dao.impl.statistics;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.statistics.UserStatsEntity;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserStatsEntityDaoImpl implements UserStatsEntityDao {
	public Uni<UserStatsEntity> findByUserId(ReactivePersistenceContext em, UUID userId) {
		return em.find(UserStatsEntity.class, userId);
	}

	@Override
	public Uni<LocalDateTime> setLastLaunched(ReactivePersistenceTxContext tx, UUID userId, LocalDateTime lastLaunched) {
		return getOrCreate(tx, userId)
				.invoke(entity -> entity.setLastLaunched(lastLaunched))
				.flatMap(tx::merge)
				.map(UserStatsEntity::getLastLaunched);
	}

	@Override
	public Uni<LocalDateTime> setLastSeen(ReactivePersistenceTxContext tx, UUID userId, LocalDateTime lastSeen) {
		return getOrCreate(tx, userId)
				.invoke(entity -> entity.setLastSeen(lastSeen))
				.flatMap(tx::merge)
				.map(UserStatsEntity::getLastSeen);
	}

	@Override
	public Uni<Integer> increaseDaysLaunched(ReactivePersistenceTxContext tx, UUID userId) {
		return getOrCreate(tx, userId)
				.invoke(entity -> entity.setDaysLaunched(toIntOr0(entity.getDaysLaunched()) + 1))
				.flatMap(tx::merge)
				.map(UserStatsEntity::getDaysLaunched);
	}

	@Override
	public Uni<Integer> increaseRecipesAssessed(ReactivePersistenceTxContext tx, UUID userId) {
		return getOrCreate(tx, userId)
				.invoke(entity -> entity.setRecipesAssessed(toIntOr0(entity.getRecipesAssessed()) + 1))
				.flatMap(tx::merge)
				.map(UserStatsEntity::getRecipesAssessed);
	}

	private Uni<UserStatsEntity> getOrCreate(ReactivePersistenceTxContext tx, UUID userId) {
		return tx.find(UserStatsEntity.class, userId)
				.flatMap(entity -> entity != null
						? Uni.createFrom().item(entity)
						: createForUser(tx, userId));
	}

	private Uni<UserStatsEntity> createForUser(ReactivePersistenceTxContext tx, UUID userId) {
		return tx.find(UserEntity.class, userId)
				.flatMap(user -> {
					UserStatsEntity userStatsEntity = new UserStatsEntity();
					userStatsEntity.setUser(user);
					userStatsEntity.setDaysLaunched(0);
					userStatsEntity.setRecipesAssessed(0);
					return tx.persist(userStatsEntity);
				});
	}

	private int toIntOr0(Integer value) {
		return value == null ? 0 : value;
	}
}
