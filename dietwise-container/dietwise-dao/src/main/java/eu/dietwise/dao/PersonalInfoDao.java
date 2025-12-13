package eu.dietwise.dao;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.v1.model.PersonalInfo;
import io.smallrye.mutiny.Uni;

/**
 * DAO interface for personal information.
 */
public interface PersonalInfoDao {
	/**
	 * Find the personal information of the given user, if any.
	 *
	 * @param em        The reactive persistence context
	 * @param hasUserId Something that contains the user id
	 * @return The personal information object, if it exists in the DB, {@code null} otherwise
	 */
	Uni<PersonalInfo> findByUser(ReactivePersistenceContext em, HasUserId hasUserId);

	/**
	 * Store (create or update) the personal information for the given user.
	 *
	 * @param tx           The reactive transactional persistence context
	 * @param hasUserId    Something that contains the user id, never {@code null}
	 * @param personalInfo The personal information object, never {@code null}
	 * @return The personal information object with the update values
	 */
	Uni<PersonalInfo> storeForUser(ReactivePersistenceTxContext tx, HasUserId hasUserId, PersonalInfo personalInfo);
}
