package eu.dietwise.services.v1.impl;

import java.time.LocalDateTime;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.UserDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.keycloak.KeycloakAdminClient;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.AccountService;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

@ApplicationScoped
public class AccountServiceImpl implements AccountService {
	private static final String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";
	private static final String ACCESS_TOKEN_FIELD = "access_token";

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserDao userDao;
	private final DateTimeService dateTimeService;
	private final Authorization authorization;
	private final KeycloakAdminClient keycloakAdminClient;
	private final String realm;
	private final String clientId;
	private final String clientSecret;

	public AccountServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserDao userDao,
			DateTimeService dateTimeService,
			Authorization authorization,
			@RestClient KeycloakAdminClient keycloakAdminClient,
			@ConfigProperty(name = "dietwise.keycloak.admin.realm") String realm,
			@ConfigProperty(name = "dietwise.keycloak.admin.client-id") String clientId,
			@ConfigProperty(name = "dietwise.keycloak.admin.client-secret") String clientSecret
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.userDao = userDao;
		this.dateTimeService = dateTimeService;
		this.authorization = authorization;
		this.keycloakAdminClient = keycloakAdminClient;
		this.realm = realm;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public Uni<Void> deleteAccount(User user) {
		authorization.requireLogin(user);
		String idmId = authorization.requireIdmId(user);
		return deleteIdentity(idmId)
				.chain(() -> tombstoneUser(idmId));
	}

	private Uni<Void> deleteIdentity(String idmId) {
		return keycloakAdminClient.getClientCredentialsToken(realm, CLIENT_CREDENTIALS_GRANT_TYPE, clientId, clientSecret)
				.map(this::requireAccessToken)
				.chain(accessToken -> keycloakAdminClient.deleteUser(realm, idmId, "Bearer " + accessToken))
				.flatMap(this::acceptDeletedOrMissing);
	}

	private Uni<Void> acceptDeletedOrMissing(RestResponse<Void> response) {
		int status = response.getStatus();
		if (status == 204 || status == 404) return Uni.createFrom().voidItem();
		return Uni.createFrom().failure(new IllegalStateException("Keycloak user deletion failed with HTTP status " + status));
	}

	private Uni<Void> tombstoneUser(String idmId) {
		LocalDateTime deletedAt = dateTimeService.getNow();
		return persistenceContextFactory.withTransaction(tx -> userDao.tombstoneByIdmId(tx, idmId, deletedAt));
	}

	private String requireAccessToken(Map<String, Object> tokenResponse) {
		if (tokenResponse != null && tokenResponse.get(ACCESS_TOKEN_FIELD) instanceof String accessToken && !accessToken.isBlank()) {
			return accessToken;
		}
		throw new IllegalStateException("Keycloak token response did not include an access token");
	}
}
