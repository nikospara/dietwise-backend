package eu.dietwise.services.authz;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.User;

@ApplicationScoped
public class AuthorizationImpl implements Authorization {
	@Override
	public void requireLogin(User user) {
		if (user == null || user.isUnauthenticated()) {
			throw new NotAuthenticatedException("this operation requires a valid, authenticated user");
		}
	}

	@Override
	public String requireIdmId(User user) {
		return user.getIdmId()
				.filter(id -> !id.isBlank())
				.orElseThrow(() -> new NotAuthenticatedException("this operation requires a user with an IDM id"));
	}

	@Override
	public String requireApplicationId(User user) {
		if (user == null || user.getApplicationId().isEmpty()) {
			throw new NotAuthorizedException("this operation requires the user to be associated with an application");
		}
		return user.getApplicationId().get();
	}
}
