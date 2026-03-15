package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.statistics.UserStatsEntityDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class StatisticsServiceImpl implements StatisticsService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final UserStatsEntityDao userStatsEntityDao;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;
	private final DateTimeService dateTimeService;
	private final Authorization authorization;

	public StatisticsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			UserStatsEntityDao userStatsEntityDao,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao,
			DateTimeService dateTimeService,
			Authorization authorization
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.userStatsEntityDao = userStatsEntityDao;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
		this.dateTimeService = dateTimeService;
		this.authorization = authorization;
	}

	@Override
	public Uni<User> markUserActivity(User user) {
		if (user == null || user.getApplicationId().isEmpty()) return Uni.createFrom().item(user);
		LocalDateTime now = dateTimeService.getNow();
		String applicationId = user.getApplicationId().get();
		UUID userId = UUID.fromString(user.getId().asString());
		return persistenceContextFactory.withTransaction(tx -> forc(
				userStatsEntityDao.setLastSeen(tx, applicationId, userId, now),
				increaseDaysLaunchedIfNeeded(tx, user, user.getApplicationId().get(), userId, now)
		));
	}

	private Function<? super LocalDateTime, Uni<? extends User>> increaseDaysLaunchedIfNeeded(ReactivePersistenceTxContext tx, User user, String applicationId, UUID userId, LocalDateTime now) {
		return lastSeen -> {
			if (lastSeen == null || lastSeen.isBefore(now.minusDays(1))) {
				return userStatsEntityDao.increaseDaysLaunched(tx, applicationId, userId).replaceWith(user);
			} else {
				return Uni.createFrom().item(user);
			}
		};
	}

	@Override
	public Uni<User> assessedRecipe(User user) {
		if (user == null || user.getApplicationId().isEmpty()) return Uni.createFrom().item(user);
		String applicationId = user.getApplicationId().get();
		UUID userId = UUID.fromString(user.getId().asString());
		return persistenceContextFactory.withTransaction(tx ->
				userStatsEntityDao.increaseRecipesAssessed(tx, applicationId, userId).replaceWith(user)
		);
	}

	@Override
	public Uni<Integer> increaseTimesAccepted(User user, SuggestionTemplateId suggestionId) {
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return persistenceContextFactory.withTransaction(tx ->
				userSuggestionStatsEntityDao.increaseTimesAccepted(tx, applicationId, user, suggestionId)
		);
	}

	@Override
	public Uni<Integer> decreaseTimesAccepted(User user, SuggestionTemplateId suggestionId) {
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return persistenceContextFactory.withTransaction(tx ->
				userSuggestionStatsEntityDao.decreaseTimesAccepted(tx, applicationId, user, suggestionId)
		);
	}

	@Override
	public Uni<Integer> increaseTimesRejected(User user, SuggestionTemplateId suggestionId) {
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return persistenceContextFactory.withTransaction(tx ->
				userSuggestionStatsEntityDao.increaseTimesRejected(tx, applicationId, user, suggestionId)
		);
	}

	@Override
	public Uni<Integer> decreaseTimesRejected(User user, SuggestionTemplateId suggestionId) {
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return persistenceContextFactory.withTransaction(tx ->
				userSuggestionStatsEntityDao.decreaseTimesRejected(tx, applicationId, user, suggestionId)
		);
	}
}
