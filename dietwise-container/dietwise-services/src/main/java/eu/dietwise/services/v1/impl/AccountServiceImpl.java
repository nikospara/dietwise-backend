package eu.dietwise.services.v1.impl;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.UserDao;
import eu.dietwise.dao.statistics.UserRecipeStatsEntityDao;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
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
	private final PersonalInfoDao personalInfoDao;
	private final UserRecipeStatsEntityDao userRecipeStatsEntityDao;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	private final UserStatsEntityDao userStatsEntityDao;
	private final DateTimeService dateTimeService;
	private final Authorization authorization;
	private final KeycloakAdminClient keycloakAdminClient;
	private final String realm;
	private final String clientId;
	private final String clientSecret;

	public AccountServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserDao userDao,
			PersonalInfoDao personalInfoDao,
			UserRecipeStatsEntityDao userRecipeStatsEntityDao,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao,
			UserStatsEntityDao userStatsEntityDao,
			DateTimeService dateTimeService,
			Authorization authorization,
			@RestClient KeycloakAdminClient keycloakAdminClient,
			@ConfigProperty(name = "dietwise.keycloak.admin.realm") String realm,
			@ConfigProperty(name = "dietwise.keycloak.admin.client-id") String clientId,
			@ConfigProperty(name = "dietwise.keycloak.admin.client-secret") String clientSecret
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.userDao = userDao;
		this.personalInfoDao = personalInfoDao;
		this.userRecipeStatsEntityDao = userRecipeStatsEntityDao;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
		this.userStatsEntityDao = userStatsEntityDao;
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
		UUID userId = authorization.requireUserUuid(user);
		return deleteIdentity(idmId)
				.chain(() -> deleteLocalAccountData(user, userId, idmId));
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

	private Uni<Void> deleteLocalAccountData(User user, UUID userId, String idmId) {
		LocalDateTime deletedAt = dateTimeService.getNow();
		return persistenceContextFactory.withTransaction(tx ->
				personalInfoDao.deleteByUser(tx, user)
						.chain(() -> userRecipeStatsEntityDao.deleteByUser(tx, userId))
						.chain(() -> userSuggestionStatsEntityDao.deleteByUser(tx, userId))
						.chain(() -> userStatsEntityDao.deleteByUser(tx, userId))
						.chain(() -> userDao.tombstoneByIdmId(tx, idmId, deletedAt))
		);
	}

	private String requireAccessToken(Map<String, Object> tokenResponse) {
		if (tokenResponse != null && tokenResponse.get(ACCESS_TOKEN_FIELD) instanceof String accessToken && !accessToken.isBlank()) {
			return accessToken;
		}
		throw new IllegalStateException("Keycloak token response did not include an access token");
	}
}
