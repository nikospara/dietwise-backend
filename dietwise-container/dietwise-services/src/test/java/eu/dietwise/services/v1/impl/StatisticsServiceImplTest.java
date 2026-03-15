package eu.dietwise.services.v1.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {
	private static final UUID USER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String APPLICATION_ID = "recipewatch";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 26, 12, 30);
	private static final SuggestionTemplateId SUGGESTION_ID = new GenericSuggestionTemplateId("22222222-2222-3333-4444-555555555555");

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	@Mock
	private UserStatsEntityDao userStatsEntityDao;
	@Mock
	private UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	@Mock
	private DateTimeService dateTimeService;
	@Mock
	private Authorization authorization;

	private StatisticsServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		lenient().when(dateTimeService.getNow()).thenReturn(NOW);
		sut = new StatisticsServiceImpl(persistenceContextFactory, userStatsEntityDao, userSuggestionStatsEntityDao, dateTimeService, authorization);
	}

	@Test
	void markUserActivityReturnsNullWhenUserIsNull() {
		User result = sut.markUserActivity(null)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isNull();
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
		verify(dateTimeService, never()).getNow();
		verify(userStatsEntityDao, never()).setLastSeen(any(), any(), any(), any());
		verify(userStatsEntityDao, never()).increaseDaysLaunched(any(), any(), any());
	}

	@Test
	void markUserActivityReturnsUserWithoutPersistenceWhenApplicationIdMissing() {
		User user = makeUserWithoutApplicationId();

		User result = sut.markUserActivity(user)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
		verify(dateTimeService, never()).getNow();
		verify(userStatsEntityDao, never()).setLastSeen(any(), any(), any(), any());
		verify(userStatsEntityDao, never()).increaseDaysLaunched(any(), any(), any());
	}

	@Test
	void markUserActivityIncreasesDaysLaunchedWhenLastSeenMoreThanOneDayAgo() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(userStatsEntityDao.setLastSeen(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(NOW.minusDays(2)));
		lenient().when(userStatsEntityDao.increaseDaysLaunched(any(), any(), any())).thenReturn(Uni.createFrom().item(1));

		User result = sut.markUserActivity(user)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(dateTimeService, times(1)).getNow();
		verify(userStatsEntityDao).setLastSeen(any(), eq(APPLICATION_ID), eq(USER_UUID), eq(NOW));
		verify(userStatsEntityDao).increaseDaysLaunched(any(), eq(APPLICATION_ID), eq(USER_UUID));
	}

	@Test
	void markUserActivitySkipsDaysLaunchedIncreaseWhenLastSeenWithinOneDay() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(userStatsEntityDao.setLastSeen(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(NOW.minusHours(12)));

		User result = sut.markUserActivity(user)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isSameAs(user);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(dateTimeService, times(1)).getNow();
		verify(userStatsEntityDao).setLastSeen(any(), eq(APPLICATION_ID), eq(USER_UUID), eq(NOW));
		verify(userStatsEntityDao, never()).increaseDaysLaunched(any(), any(), any());
	}

	@Test
	void testIncreaseTimesAccepted() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(userSuggestionStatsEntityDao.increaseTimesAccepted(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(11));

		Integer result = sut.increaseTimesAccepted(user, SUGGESTION_ID).await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isEqualTo(11);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(userSuggestionStatsEntityDao, times(1)).increaseTimesAccepted(any(), eq(APPLICATION_ID), eq(user), eq(SUGGESTION_ID));
		verifyNoMoreInteractions(userSuggestionStatsEntityDao);
		verify(authorization).requireLogin(user);
		verify(authorization).requireApplicationId(user);
	}

	@Test
	void testDecreaseTimesAccepted() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(userSuggestionStatsEntityDao.decreaseTimesAccepted(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(11));

		Integer result = sut.decreaseTimesAccepted(user, SUGGESTION_ID).await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isEqualTo(11);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(userSuggestionStatsEntityDao, times(1)).decreaseTimesAccepted(any(), eq(APPLICATION_ID), eq(user), eq(SUGGESTION_ID));
		verifyNoMoreInteractions(userSuggestionStatsEntityDao);
		verify(authorization).requireLogin(user);
		verify(authorization).requireApplicationId(user);
	}

	@Test
	void testIncreaseTimesRejected() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(userSuggestionStatsEntityDao.increaseTimesRejected(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(11));

		Integer result = sut.increaseTimesRejected(user, SUGGESTION_ID).await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isEqualTo(11);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(userSuggestionStatsEntityDao, times(1)).increaseTimesRejected(any(), eq(APPLICATION_ID), eq(user), eq(SUGGESTION_ID));
		verifyNoMoreInteractions(userSuggestionStatsEntityDao);
		verify(authorization).requireLogin(user);
		verify(authorization).requireApplicationId(user);
	}

	@Test
	void testDecreaseTimesRejected() {
		User user = makeUserWithApplicationId(APPLICATION_ID);
		when(authorization.requireApplicationId(user)).thenReturn(APPLICATION_ID);
		when(userSuggestionStatsEntityDao.decreaseTimesRejected(any(), any(), any(), any())).thenReturn(Uni.createFrom().item(11));

		Integer result = sut.decreaseTimesRejected(user, SUGGESTION_ID).await().atMost(Duration.ofSeconds(5L));

		assertThat(result).isEqualTo(11);
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(userSuggestionStatsEntityDao, times(1)).decreaseTimesRejected(any(), eq(APPLICATION_ID), eq(user), eq(SUGGESTION_ID));
		verifyNoMoreInteractions(userSuggestionStatsEntityDao);
		verify(authorization).requireLogin(user);
		verify(authorization).requireApplicationId(user);
	}

	private static User makeUserWithApplicationId(String applicationId) {
		return ImmutableUser.builder()
				.id(new UserIdImpl(USER_UUID.toString()))
				.name("user@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.noneOf(Role.class))
				.applicationId(applicationId)
				.build();
	}

	private static User makeUserWithoutApplicationId() {
		return ImmutableUser.builder()
				.id(new UserIdImpl(USER_UUID.toString()))
				.name("user@example.test")
				.email(null)
				.isService(false)
				.isSystem(false)
				.isUnauthenticated(false)
				.roles(EnumSet.noneOf(Role.class))
				.build();
	}
}
