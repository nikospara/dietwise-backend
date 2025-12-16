package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.services.v1.PersonalInfoService;
import eu.dietwise.v1.model.PersonalInfo;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class PersonalInfoServiceImpl implements PersonalInfoService {
	private final PersonalInfoDao personalInfoDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	public PersonalInfoServiceImpl(PersonalInfoDao personalInfoDao, ReactivePersistenceContextFactory persistenceContextFactory) {
		this.personalInfoDao = personalInfoDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<PersonalInfo> findByUser(User user) {
		return forc(
				authorizeUser(user),
				() -> persistenceContextFactory.withTransaction(tx -> personalInfoDao.findByUser(tx, user))
		);
	}

	@Override
	public Uni<PersonalInfo> storeForUser(User user, PersonalInfo personalInfo) {
		return forc(
				authorizeUser(user),
				() -> persistenceContextFactory.withTransaction(tx -> personalInfoDao.storeForUser(tx, user, personalInfo))
		);
	}

	private Uni<Void> authorizeUser(User user) {
		if (user == null || user.isUnauthenticated()) return Uni.createFrom().failure(new NotAuthenticatedException());
		if (!user.getRoles().contains(Role.CITIZEN)) return Uni.createFrom().failure(new NotAuthorizedException());
		return Uni.createFrom().nullItem();
	}
}
