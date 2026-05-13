package eu.dietwise.dao;

import java.time.LocalDateTime;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.UserData;
import io.smallrye.mutiny.Uni;

/**
 * DAO interface for user information.
 */
public interface UserDao {
	Uni<UserData> findOrCreateByIdmId(ReactivePersistenceTxContext tx, String idmId);

	/** Tombstone (mark the account as deleted, see {@code UserEntity.deletedAt}) the user with the given IDM ID. */
	Uni<Void> tombstoneByIdmId(ReactivePersistenceTxContext tx, String idmId, LocalDateTime deletedAt);
}
