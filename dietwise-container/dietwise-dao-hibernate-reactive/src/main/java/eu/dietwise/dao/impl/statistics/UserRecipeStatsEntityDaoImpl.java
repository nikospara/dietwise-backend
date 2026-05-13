package eu.dietwise.dao.impl.statistics;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.CriteriaDelete;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.statistics.UserRecipeStatsEntity;
import eu.dietwise.dao.jpa.statistics.UserRecipeStatsEntity_;
import eu.dietwise.dao.jpa.statistics.UserRecipeStatsId;
import eu.dietwise.dao.statistics.UserRecipeStatsEntityDao;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class UserRecipeStatsEntityDaoImpl implements UserRecipeStatsEntityDao {
	@Override
	public Uni<Integer> increaseTimesAssessed(
			ReactivePersistenceTxContext tx,
			String applicationId,
			UUID userId,
			String recipeUrl,
			String recipeName,
			LocalDateTime lastAssessed
	) {
		return getOrCreate(tx, applicationId, userId, recipeUrl)
				.map(entity -> {
					entity.setRecipeName(recipeName);
					entity.setLastAssessed(lastAssessed);
					entity.setTimesAssessed(toIntOr0(entity.getTimesAssessed()) + 1);
					return entity;
				})
				.flatMap(entity -> tx.merge(entity).replaceWith(entity.getTimesAssessed()));
	}

	@Override
	public Uni<Void> deleteByUser(ReactivePersistenceTxContext tx, UUID userId) {
		var cb = tx.getCriteriaBuilder();
		CriteriaDelete<UserRecipeStatsEntity> delete = cb.createCriteriaDelete(UserRecipeStatsEntity.class);
		var root = delete.from(UserRecipeStatsEntity.class);
		delete.where(cb.equal(root.get(UserRecipeStatsEntity_.userId), userId));
		return tx.createDelete(delete).execute().replaceWithVoid();
	}

	private Uni<UserRecipeStatsEntity> getOrCreate(ReactivePersistenceTxContext tx, String applicationId, UUID userId, String recipeUrl) {
		return tx.find(UserRecipeStatsEntity.class, new UserRecipeStatsId(userId, applicationId, recipeUrl))
				.flatMap(entity -> entity != null
						? Uni.createFrom().item(entity)
						: create(tx, applicationId, userId, recipeUrl));
	}

	private Uni<UserRecipeStatsEntity> create(ReactivePersistenceTxContext tx, String applicationId, UUID userId, String recipeUrl) {
		return tx.find(UserEntity.class, userId)
				.flatMap(user -> {
					UserRecipeStatsEntity entity = new UserRecipeStatsEntity();
					entity.setUserId(user.getId());
					entity.setApplicationId(applicationId);
					entity.setRecipeUrl(recipeUrl);
					entity.setUser(user);
					entity.setTimesAssessed(0);
					return tx.persist(entity);
				});
	}

	private int toIntOr0(Integer value) {
		return value == null ? 0 : value;
	}
}
