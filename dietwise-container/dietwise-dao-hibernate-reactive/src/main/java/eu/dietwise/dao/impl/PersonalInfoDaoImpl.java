package eu.dietwise.dao.impl;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.common.v1.types.UserId;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.jpa.PersonalInfoEntity;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.PersonalInfo;
import io.smallrye.mutiny.Uni;

/**
 * Implementation of the {@link PersonalInfoDao} with Hibernate Reactive.
 */
@ApplicationScoped
public class PersonalInfoDaoImpl implements PersonalInfoDao {
	@Override
	public Uni<PersonalInfo> findByUser(ReactivePersistenceContext em, HasUserId hasUserId) {
		return makeUserUuid(hasUserId)
				.flatMap(uuid -> em.find(PersonalInfoEntity.class, uuid))
				.map(this::toPersonalInfo);
	}

	@Override
	public Uni<PersonalInfo> storeForUser(ReactivePersistenceTxContext tx, HasUserId hasUserId, PersonalInfo personalInfo) {
		return makeUserUuid(hasUserId)
				.flatMap(uuid -> tx.find(PersonalInfoEntity.class, uuid))
				.flatMap(entity -> {
					if (entity != null) {
						entity.setGender(personalInfo.getGender());
						entity.setYearOfBirth(personalInfo.getYearOfBirth());
						return tx.merge(entity);
					} else {
						entity = new PersonalInfoEntity();
						entity.setGender(personalInfo.getGender());
						entity.setYearOfBirth(personalInfo.getYearOfBirth());
						return tx.persist(entity);
					}
				})
				.map(this::toPersonalInfo);
	}

	private Uni<UUID> makeUserUuid(HasUserId hasUserId) {
		if (hasUserId == null) {
			return Uni.createFrom().failure(new IllegalArgumentException("hasUserId is null"));
		}
		UserId userId = hasUserId.getId();
		if (userId == null) {
			return Uni.createFrom().failure(new IllegalArgumentException("userId is null"));
		}
		try {
			return Uni.createFrom().item(UUID.fromString(userId.asString()));
		} catch (IllegalArgumentException e) {
			return Uni.createFrom().failure(new IllegalArgumentException(String.format("Cannot convert userId to UUID: %s", userId.asString()), e));
		}
	}

	private PersonalInfo toPersonalInfo(PersonalInfoEntity entity) {
		return entity == null ? null : ImmutablePersonalInfo.builder().gender(entity.getGender()).yearOfBirth(entity.getYearOfBirth()).build();
	}
}
