package eu.dietwise.services.v1.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.v1.PersonalInfoService;
import eu.dietwise.v1.model.PersonalInfo;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class PersonalInfoServiceImpl implements PersonalInfoService {
	private final PersonalInfoDao personalInfoDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final Authorization authorization;

	public PersonalInfoServiceImpl(PersonalInfoDao personalInfoDao, ReactivePersistenceContextFactory persistenceContextFactory, Authorization authorization) {
		this.personalInfoDao = personalInfoDao;
		this.persistenceContextFactory = persistenceContextFactory;
		this.authorization = authorization;
	}

	@Override
	public Uni<PersonalInfo> findByUser(User user) {
		authorization.requireCitizen(user);
		return persistenceContextFactory.withTransaction(tx -> personalInfoDao.findByUser(tx, user));
	}

	@Override
	public Uni<PersonalInfo> storeForUser(User user, PersonalInfo personalInfo) {
		authorization.requireCitizen(user);
		return persistenceContextFactory.withTransaction(tx -> personalInfoDao.storeForUser(tx, user, personalInfo));
	}
}
