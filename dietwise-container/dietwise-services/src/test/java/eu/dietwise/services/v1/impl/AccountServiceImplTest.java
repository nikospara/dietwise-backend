package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.UserDao;
import eu.dietwise.dao.statistics.UserRecipeStatsEntityDao;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.keycloak.KeycloakAdminClient;
import eu.dietwise.services.nondomain.DateTimeService;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {
	private static final UUID USER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String IDM_ID = "33333333-2222-3333-4444-555555555555";
	private static final String REALM = "dietwise";
	private static final String CLIENT_ID = "account-admin";
	private static final String CLIENT_SECRET = "secret";
	private static final String ACCESS_TOKEN = "access-token";
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 5, 13, 12, 30);

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Mock
	private UserDao userDao;
	@Mock
	private PersonalInfoDao personalInfoDao;
	@Mock
	private UserRecipeStatsEntityDao userRecipeStatsEntityDao;
	@Mock
	private UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	@Mock
	private UserStatsEntityDao userStatsEntityDao;
	@Mock
	private DateTimeService dateTimeService;
	@Mock
	private KeycloakAdminClient keycloakAdminClient;

	private AccountServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new AccountServiceImpl(
				persistenceContextFactory,
				userDao,
				personalInfoDao,
				userRecipeStatsEntityDao,
				userSuggestionStatsEntityDao,
				userStatsEntityDao,
				dateTimeService,
				new AuthorizationImpl(),
				keycloakAdminClient,
				REALM,
				CLIENT_ID,
				CLIENT_SECRET
		);
	}

	@Test
	void deleteAccountDeletesKeycloakUserLocalDataAndTombstonesUser() {
		User user = makeUserWithIdmId();
		RestResponse<Void> deleteResponse = makeResponse(204);
		when(keycloakAdminClient.getClientCredentialsToken(REALM, "client_credentials", CLIENT_ID, CLIENT_SECRET))
				.thenReturn(Uni.createFrom().item(Map.of("access_token", ACCESS_TOKEN)));
		when(keycloakAdminClient.deleteUser(REALM, IDM_ID, "Bearer " + ACCESS_TOKEN))
				.thenReturn(Uni.createFrom().item(deleteResponse));
		when(dateTimeService.getNow()).thenReturn(DELETED_AT);
		when(personalInfoDao.deleteByUser(any(), eq(user))).thenReturn(Uni.createFrom().voidItem());
		when(userRecipeStatsEntityDao.deleteByUser(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().voidItem());
		when(userSuggestionStatsEntityDao.deleteByUser(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().voidItem());
		when(userStatsEntityDao.deleteByUser(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().voidItem());
		when(userDao.tombstoneByIdmId(any(), eq(IDM_ID), eq(DELETED_AT))).thenReturn(Uni.createFrom().voidItem());

		Void result = sut.deleteAccount(user).await().atMost(Duration.ofSeconds(5));

		assertThat(result).isNull();
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		InOrder inOrder = inOrder(keycloakAdminClient, personalInfoDao, userRecipeStatsEntityDao, userSuggestionStatsEntityDao, userStatsEntityDao, userDao);
		inOrder.verify(keycloakAdminClient).getClientCredentialsToken(REALM, "client_credentials", CLIENT_ID, CLIENT_SECRET);
		inOrder.verify(keycloakAdminClient).deleteUser(REALM, IDM_ID, "Bearer " + ACCESS_TOKEN);
		inOrder.verify(personalInfoDao).deleteByUser(any(), eq(user));
		inOrder.verify(userRecipeStatsEntityDao).deleteByUser(any(), eq(USER_UUID));
		inOrder.verify(userSuggestionStatsEntityDao).deleteByUser(any(), eq(USER_UUID));
		inOrder.verify(userStatsEntityDao).deleteByUser(any(), eq(USER_UUID));
		inOrder.verify(userDao).tombstoneByIdmId(any(), eq(IDM_ID), eq(DELETED_AT));
	}

	@Test
	void deleteAccountTreatsMissingKeycloakUserAsDeleted() {
		User user = makeUserWithIdmId();
		RestResponse<Void> deleteResponse = makeResponse(404);
		when(keycloakAdminClient.getClientCredentialsToken(REALM, "client_credentials", CLIENT_ID, CLIENT_SECRET))
				.thenReturn(Uni.createFrom().item(Map.of("access_token", ACCESS_TOKEN)));
		when(keycloakAdminClient.deleteUser(REALM, IDM_ID, "Bearer " + ACCESS_TOKEN))
				.thenReturn(Uni.createFrom().item(deleteResponse));
		when(dateTimeService.getNow()).thenReturn(DELETED_AT);
		when(personalInfoDao.deleteByUser(any(), eq(user))).thenReturn(Uni.createFrom().voidItem());
		when(userRecipeStatsEntityDao.deleteByUser(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().voidItem());
		when(userSuggestionStatsEntityDao.deleteByUser(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().voidItem());
		when(userStatsEntityDao.deleteByUser(any(), eq(USER_UUID))).thenReturn(Uni.createFrom().voidItem());
		when(userDao.tombstoneByIdmId(any(), eq(IDM_ID), eq(DELETED_AT))).thenReturn(Uni.createFrom().voidItem());

		Void result = sut.deleteAccount(user).await().atMost(Duration.ofSeconds(5));

		assertThat(result).isNull();
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(userDao).tombstoneByIdmId(any(), eq(IDM_ID), eq(DELETED_AT));
	}

	// TODO This is more like a test for the AuthorizationImpl
	@Test
	void deleteAccountFailsWhenUserHasNoIdmId() {
		User user = makeUserWithoutIdmId();

		assertThatThrownBy(() -> sut.deleteAccount(user))
				.isInstanceOf(NotAuthenticatedException.class)
				.hasMessage("this operation requires a user with an IDM id");

		verify(keycloakAdminClient, never()).getClientCredentialsToken(any(), any(), any(), any());
		verify(personalInfoDao, never()).deleteByUser(any(), any());
		verify(userDao, never()).tombstoneByIdmId(any(), any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	// TODO This is more like a test for the AuthorizationImpl
	@Test
	void deleteAccountFailsWhenUserHasNoLocalId() {
		User user = makeUserWithoutLocalId();

		assertThatThrownBy(() -> sut.deleteAccount(user))
				.isInstanceOf(NotAuthenticatedException.class)
				.hasMessage("this operation requires a user with a local id");

		verify(keycloakAdminClient, never()).getClientCredentialsToken(any(), any(), any(), any());
		verify(personalInfoDao, never()).deleteByUser(any(), any());
		verify(userDao, never()).tombstoneByIdmId(any(), any(), any());
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	@SuppressWarnings("unchecked")
	private static RestResponse<Void> makeResponse(int status) {
		RestResponse<Void> response = (RestResponse<Void>) mock(RestResponse.class);
		when(response.getStatus()).thenReturn(status);
		return response;
	}

	private static User makeUserWithIdmId() {
		return ImmutableUser.builder()
				.id(new UserIdImpl(USER_UUID.toString()))
				.name("user@example.test")
				.email(null)
				.idmId(IDM_ID)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.of(Role.CITIZEN))
				.build();
	}

	private static User makeUserWithoutIdmId() {
		return ImmutableUser.builder()
				.id(new UserIdImpl(USER_UUID.toString()))
				.name("user@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.of(Role.CITIZEN))
				.build();
	}

	private static User makeUserWithoutLocalId() {
		return ImmutableUser.builder()
				.id(null)
				.name("user@example.test")
				.email(null)
				.idmId(IDM_ID)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.of(Role.CITIZEN))
				.build();
	}
}
