package eu.dietwise.services.v1;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.v1.model.PersonalInfo;
import io.smallrye.mutiny.Uni;

/**
 * Service interface for personal information.
 */
public interface PersonalInfoService {
	/**
	 * Find the personal information of the given user, if any.
	 *
	 * @param user The user
	 * @return The personal information object, if it exists in the DB, {@code null} otherwise
	 */
	Uni<PersonalInfo> findByUser(User user);

	/**
	 * Store (create or update) the personal information for the given user.
	 *
	 * @param user         The user
	 * @param personalInfo The personal information object, never {@code null}
	 * @return The personal information object with the update values
	 */
	Uni<PersonalInfo> storeForUser(User user, PersonalInfo personalInfo);
}
