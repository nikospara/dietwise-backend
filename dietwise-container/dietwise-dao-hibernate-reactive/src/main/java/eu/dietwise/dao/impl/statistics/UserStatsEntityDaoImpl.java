package eu.dietwise.dao.impl.statistics;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.statistics.UserStatsEntity;
import eu.dietwise.dao.jpa.statistics.UserStatsId;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;

@ApplicationScoped
public class UserStatsEntityDaoImpl implements UserStatsEntityDao {
	public Uni<UserStatsEntity> findByUserId(ReactivePersistenceContext em, String applicationId, UUID userId) {
		return em.find(UserStatsEntity.class, new UserStatsId(userId, applicationId));
	}

	@Override
	public Uni<LocalDateTime> setLastLaunched(ReactivePersistenceTxContext tx, String applicationId, UUID userId, LocalDateTime lastLaunched) {
		return getOrCreate(tx, applicationId, userId)
				.map(entity -> Tuple2.of(entity, entity.getLastLaunched()))
				.invoke(entityAndVal -> entityAndVal.getItem1().setLastLaunched(lastLaunched))
				.flatMap(entityAndVal -> tx.merge(entityAndVal.getItem1()).replaceWith(entityAndVal.getItem2()));
	}

	@Override
	public Uni<LocalDateTime> setLastSeen(ReactivePersistenceTxContext tx, String applicationId, UUID userId, LocalDateTime lastSeen) {
		return getOrCreate(tx, applicationId, userId)
				.map(entity -> Tuple2.of(entity, entity.getLastSeen()))
				.invoke(entityAndVal -> entityAndVal.getItem1().setLastSeen(lastSeen))
				.flatMap(entityAndVal -> tx.merge(entityAndVal.getItem1()).replaceWith(entityAndVal.getItem2()));
	}

	@Override
	public Uni<Integer> increaseDaysLaunched(ReactivePersistenceTxContext tx, String applicationId, UUID userId) {
		return getOrCreate(tx, applicationId, userId)
				.map(entity -> Tuple2.of(entity, toIntOr0(entity.getDaysLaunched())))
				.invoke(entityAndVal -> entityAndVal.getItem1().setDaysLaunched(toIntOr0(entityAndVal.getItem1().getDaysLaunched()) + 1))
				.flatMap(entityAndVal -> tx.merge(entityAndVal.getItem1()).replaceWith(entityAndVal.getItem2()));
	}

	@Override
	public Uni<Integer> increaseRecipesAssessed(ReactivePersistenceTxContext tx, String applicationId, UUID userId) {
		return getOrCreate(tx, applicationId, userId)
				.map(entity -> Tuple2.of(entity, toIntOr0(entity.getRecipesAssessed())))
				.invoke(entityAndVal -> entityAndVal.getItem1().setRecipesAssessed(toIntOr0(entityAndVal.getItem1().getRecipesAssessed()) + 1))
				.flatMap(entityAndVal -> tx.merge(entityAndVal.getItem1()).replaceWith(entityAndVal.getItem2()));
	}

	private Uni<UserStatsEntity> getOrCreate(ReactivePersistenceTxContext tx, String applicationId, UUID userId) {
		return tx.find(UserStatsEntity.class, new UserStatsId(userId, applicationId))
				.flatMap(entity -> entity != null
						? Uni.createFrom().item(entity)
						: createForUser(tx, applicationId, userId));
	}

	private Uni<UserStatsEntity> createForUser(ReactivePersistenceTxContext tx, String applicationId, UUID userId) {
		return tx.find(UserEntity.class, userId)
				.flatMap(user -> {
					// Here we implicitly demand the UserEntity to exist, which will be true because of the implementation of the application
					UserStatsEntity userStatsEntity = new UserStatsEntity();
					userStatsEntity.setUserId(user.getId());
					userStatsEntity.setApplicationId(applicationId);
					userStatsEntity.setDaysLaunched(0);
					userStatsEntity.setRecipesAssessed(0);
					return tx.persist(userStatsEntity);
				});
	}

	private int toIntOr0(Integer value) {
		return value == null ? 0 : value;
	}
}
