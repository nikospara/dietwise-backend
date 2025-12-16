package eu.dietwise.dao;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.UserData;
import io.smallrye.mutiny.Uni;

/**
 * DAO interface for user information.
 */
public interface UserDao {
	Uni<UserData> findOrCreateByIdmId(ReactivePersistenceTxContext tx, String idmId);
}
