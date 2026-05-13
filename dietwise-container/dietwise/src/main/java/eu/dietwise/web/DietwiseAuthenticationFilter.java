package eu.dietwise.web;

import java.util.EnumSet;
import java.util.Optional;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;

import eu.dietwise.common.types.EmailAddress;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.model.UserData;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.jaxrs.v1.security.AllowDeletedAccount;
import eu.dietwise.services.nondomain.UserService;
import eu.dietwise.services.v1.StatisticsService;
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
	private final StatisticsService statisticsService;

	public DietwiseAuthenticationFilter(UserService userService, SecurityIdentity securityIdentity, StatisticsService statisticsService) {
		this.userService = userService;
		this.securityIdentity = securityIdentity;
		this.statisticsService = statisticsService;
	}

	@ServerRequestFilter
	public Uni<Void> filter(ContainerRequestContext requestContext, ResourceInfo resourceInfo) {
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
			Optional<String> applicationId = jwtPrincipal.claim(Claims.azp).map(Object::toString);
			if (applicationId.filter("recipewatch"::equals).isPresent()) roles.add(Role.CITIZEN);
			if (applicationId.filter("rca"::equals).isPresent()) roles.add(Role.INFLUENCER);
			boolean allowDeletedAccount = allowsDeletedAccount(resourceInfo);
			return findUserData(allowDeletedAccount, sub)
					.map(userData -> toUser(userData, sub, email, roles, applicationId))
					.invoke(user -> requestContext.setSecurityContext(new DietwiseSecurityContextImpl(requestContext.getSecurityContext(), user)))
					.flatMap(user -> markActivityIfNeeded(allowDeletedAccount, user))
					.replaceWithVoid();
		}
	}

	private Uni<UserData> findUserData(boolean allowDeletedAccount, String idmId) {
		if (!allowDeletedAccount) {
			return userService.findOrCreateByIdmId(idmId);
		}
		return userService.findByIdmId(idmId)
				.flatMap(userData -> userData == null
						? userService.findOrCreateByIdmId(idmId)
						: Uni.createFrom().item(userData));
	}

	private User toUser(UserData userData, String idmId, String email, EnumSet<Role> roles, Optional<String> applicationId) {
		return ImmutableUser.builder()
				.id(userData.getId())
				.name(email)
				.email(EmailAddress.of(email))
				.idmId(idmId)
				.deletedAt(userData.getDeletedAt())
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(roles)
				.applicationId(applicationId)
				.build();
	}

	private Uni<User> markActivityIfNeeded(boolean allowDeletedAccount, User user) {
		if (allowDeletedAccount) {
			return Uni.createFrom().item(user);
		}
		return statisticsService.markUserActivity(user);
	}

	private boolean allowsDeletedAccount(ResourceInfo resourceInfo) {
		if (resourceInfo == null) return false;
		var resourceMethod = resourceInfo.getResourceMethod();
		if (resourceMethod != null && resourceMethod.isAnnotationPresent(AllowDeletedAccount.class)) return true;
		var resourceClass = resourceInfo.getResourceClass();
		return resourceClass != null && resourceClass.isAnnotationPresent(AllowDeletedAccount.class);
	}
}
