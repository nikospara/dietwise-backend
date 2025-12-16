package eu.dietwise.web;

import java.util.EnumSet;
import jakarta.ws.rs.container.ContainerRequestContext;

import eu.dietwise.common.types.EmailAddress;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.services.nondomain.UserService;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.Claims;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Replace the {@code jakarta.ws.rs.core.SecurityContext} provided by Quarkus OIDC with the application-specific one
 * that knows and uses the application-specific model.
 */
public class DietwiseAuthenticationFilter {
	private final UserService userService;
	private final SecurityIdentity securityIdentity;

	public DietwiseAuthenticationFilter(UserService userService, SecurityIdentity securityIdentity) {
		this.userService = userService;
		this.securityIdentity = securityIdentity;
	}

	@ServerRequestFilter
	public Uni<Void> filter(ContainerRequestContext requestContext) {
		if (requestContext.getSecurityContext().getUserPrincipal() == null) {
			var user = ImmutableUser.builder()
					.id(null)
					.name(null)
					.email(null)
					.isService(false)
					.isSystem(false)
					.isUnauthenticated(true)
					.roles(EnumSet.noneOf(Role.class))
					.build();
			requestContext.setSecurityContext(new DietwiseSecurityContextImpl(requestContext.getSecurityContext(), user));
			return Uni.createFrom().nullItem();
		} else {
			var jwtPrincipal = securityIdentity.getPrincipal(OidcJwtCallerPrincipal.class);
			String sub = jwtPrincipal.claim(Claims.sub).map(Object::toString).orElse(null);
			if (sub == null) return Uni.createFrom().failure(() -> new NotAuthenticatedException("sub is null"));
			String email = jwtPrincipal.claim(Claims.email).map(Object::toString).orElse(null);
			EnumSet<Role> roles = EnumSet.noneOf(Role.class);
			if (jwtPrincipal.claim(Claims.azp).filter("recipewatch"::equals).isPresent()) roles.add(Role.CITIZEN);
			if (jwtPrincipal.claim(Claims.azp).filter("rca"::equals).isPresent()) roles.add(Role.INFLUENCER);
			return userService.findOrCreateByIdmId(sub)
					.invoke(userData -> {
						var user = ImmutableUser.builder()
								.id(userData.getId())
								.name(email)
								.email(EmailAddress.of(email))
								.isService(false)
								.isSystem(false)
								.isUnauthenticated(false)
								.roles(roles)
								.build();
						requestContext.setSecurityContext(new DietwiseSecurityContextImpl(requestContext.getSecurityContext(), user));
					})
					.replaceWithVoid();
		}
	}
}
