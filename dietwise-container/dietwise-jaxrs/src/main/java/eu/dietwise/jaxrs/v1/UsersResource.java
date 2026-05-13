package eu.dietwise.jaxrs.v1;

import java.time.Duration;
import java.time.Instant;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.jaxrs.v1.security.AllowDeletedAccount;
import eu.dietwise.services.v1.AccountService;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("users")
public class UsersResource {
	private static final Duration MAX_AUTHENTICATION_AGE = Duration.ofMinutes(5);

	@Inject
	JsonWebToken jwt;

	@Inject
	AccountService accountService;

	@DELETE
	@Path("me")
	@AllowDeletedAccount
	public Uni<Response> deleteCurrentUser(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		requireFreshAuthentication();
		if (user.isDeleted()) {
			return Uni.createFrom().item(Response.noContent().build());
		}
		return accountService
				.deleteAccount(user)
				.replaceWith(Response.noContent().build());
	}

	private void requireFreshAuthentication() {
		Long authTime = readAuthTime();
		if (authTime == null) {
			throw new NotAuthenticatedException("auth_time is missing");
		}
		Instant authenticatedAt = Instant.ofEpochSecond(authTime);
		if (authenticatedAt.isBefore(Instant.now().minus(MAX_AUTHENTICATION_AGE))) {
			throw new NotAuthenticatedException("authentication is stale");
		}
	}

	private Long readAuthTime() {
		Object authTime = jwt.getClaim(Claims.auth_time.name());
		if (authTime instanceof Number authTimeNumber) {
			return authTimeNumber.longValue();
		}
		if (authTime instanceof String authTimeString) {
			try {
				return Long.valueOf(authTimeString);
			} catch (NumberFormatException _) {
				return null;
			}
		}
		return null;
	}
}
