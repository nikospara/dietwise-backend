package eu.dietwise.services.authz;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.UserId;

@ApplicationScoped
public class AuthorizationImpl implements Authorization {

	public static final String REQUIRES_VALID_AUTHENTICATED_USER = "this operation requires a valid, authenticated user";

	@Override
	public void requireLogin(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		}
	}

	@Override
	public void requireCitizen(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (!user.getRoles().contains(Role.CITIZEN)) {
			throw new NotAuthorizedException("this operation is only allowed for citizens");
		}
	}

	@Override
	public String requireIdmId(User user) {
		return user.getIdmId()
				.filter(id -> !id.isBlank())
				.orElseThrow(() -> new NotAuthenticatedException("this operation requires a user with an IDM id"));
	}

	@Override
	public UUID requireUserUuid(User user) {
		UserId userId = user.getId();
		if (userId == null || userId.asString().isBlank()) {
			throw new NotAuthenticatedException("this operation requires a user with a local id");
		}
		try {
			return UUID.fromString(userId.asString());
		} catch (IllegalArgumentException e) {
			throw new NotAuthenticatedException("this operation requires a user with a local UUID id", e);
		}
	}

	@Override
	public String requireApplicationId(User user) {
		if (user == null || user.getApplicationId().isEmpty()) {
			throw new NotAuthorizedException("this operation requires the user to be associated with an application");
		}
		return user.getApplicationId().get();
	}

	@Override
	public void requireAdmin(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException(REQUIRES_VALID_AUTHENTICATED_USER);
		} else if (!user.getRoles().contains(Role.ADMIN)) {
			throw new NotAuthorizedException("this operation requires admin privileges");
		}
	}
}
