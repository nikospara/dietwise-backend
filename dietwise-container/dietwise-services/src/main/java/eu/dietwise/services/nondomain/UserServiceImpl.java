package eu.dietwise.services.nondomain;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.UserData;
import eu.dietwise.dao.UserDao;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
class UserServiceImpl implements UserService {
	private final UserDao userDao;
	private final ReactivePersistenceContextFactory persistenceContextFactory;

	public UserServiceImpl(UserDao userDao, ReactivePersistenceContextFactory persistenceContextFactory) {
		this.userDao = userDao;
		this.persistenceContextFactory = persistenceContextFactory;
	}

	@Override
	public Uni<UserData> findOrCreateByIdmId(String idmId) {
		return persistenceContextFactory.withTransaction(tx -> userDao.findOrCreateByIdmId(tx, idmId));
	}
}
