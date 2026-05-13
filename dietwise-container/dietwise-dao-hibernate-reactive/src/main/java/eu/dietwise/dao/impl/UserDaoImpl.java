package eu.dietwise.dao.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.Root;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.v1.model.ImmutableUserData;
import eu.dietwise.common.v1.model.UserData;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.UserDao;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.UserEntity_;
import io.smallrye.mutiny.Uni;

/**
 * Implementation of the {@link UserDao} with Hibernate Reactive.
 */
@ApplicationScoped
public class UserDaoImpl implements UserDao {
	@Override
	public Uni<UserData> findOrCreateByIdmId(ReactivePersistenceTxContext tx, String idmId) {
		return findByIdmId(tx, idmId)
				.flatMap(userEntityOpt -> userEntityOpt
						.map(this::requireNotDeleted)
						.orElseGet(() -> createUser(tx, idmId)))
				.map(userEntity -> ImmutableUserData.builder().id(new UserIdImpl(userEntity.getId().toString())).build());
	}

	@Override
	public Uni<Void> tombstoneByIdmId(ReactivePersistenceTxContext tx, String idmId, LocalDateTime deletedAt) {
		return findByIdmId(tx, idmId)
				.flatMap(userEntityOpt -> userEntityOpt
						.map(userEntity -> Uni.createFrom().item(userEntity))
						.orElseGet(() -> createUser(tx, idmId)))
				.invoke(userEntity -> userEntity.setDeletedAt(deletedAt))
				.flatMap(userEntity -> tx.merge(userEntity).replaceWithVoid());
	}

	private Uni<Optional<UserEntity>> findByIdmId(ReactivePersistenceContext em, String idmId) {
		var cb = em.getCriteriaBuilder();
		var q = cb.createQuery(UserEntity.class);
		Root<UserEntity> userEntity = q.from(UserEntity.class);
		q.where(cb.equal(userEntity.get(UserEntity_.idmId), idmId));
		return em.createQuery(q).getSingleOptionalResult();
	}

	private Uni<UserEntity> createUser(ReactivePersistenceTxContext tx, String idmId) {
		var user = new UserEntity();
		user.setId(UUID.randomUUID());
		user.setIdmId(idmId);
		return tx.persist(user);
	}

	private Uni<UserEntity> requireNotDeleted(UserEntity userEntity) {
		if (userEntity.getDeletedAt() != null) {
			return Uni.createFrom().failure(new NotAuthenticatedException("the account has been deleted"));
		}
		return Uni.createFrom().item(userEntity);
	}
}
