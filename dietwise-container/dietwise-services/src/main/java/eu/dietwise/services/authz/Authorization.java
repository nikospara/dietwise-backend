package eu.dietwise.services.authz;

import java.util.UUID;

import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.User;

public interface Authorization {
	/**
	 * Require a valid, non-null, authenticated user, throw {@link NotAuthenticatedException} if not.
	 */
	void requireLogin(User user);

	String requireIdmId(User user);

	UUID requireUserUuid(User user);

	/**
	 * Require a valid, non-null, application, throw {@code NotAuthorizedException} if not.
	 *
	 * @param user The user, assumed not null (test with {@link #requireLogin(User)} first)
	 * @return The application id
	 * @throws NotAuthorizedException If the user object contains no application id
	 */
	String requireApplicationId(User user);
}
